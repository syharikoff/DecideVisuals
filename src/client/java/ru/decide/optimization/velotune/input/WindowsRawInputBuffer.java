package ru.decide.optimization.velotune.input;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import ru.decide.optimization.velotune.VeloTuneManager;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
public final class WindowsRawInputBuffer
implements AutoCloseable {
    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    private static final int WM_INPUT = 255;
    private static final int WM_CLOSE = 16;
    private static final int RID_INPUT = 0x10000003;
    private static final int RIM_TYPEMOUSE = 0;
    private static final int RIDEV_REMOVE = 1;
    private static final int RIDEV_NOLEGACY = 48;
    private static final int RIDEV_INPUTSINK = 256;
    private static final int MOUSE_MOVE_ABSOLUTE = 1;
    private static final int RI_MOUSE_WHEEL = 1024;
    private static final int RI_MOUSE_HWHEEL = 2048;
    private static final int WHEEL_DELTA = 120;
    private static final int INPUT_BUFFER_SIZE = 256;
    private static final int MAX_QUEUED_EVENTS = 1024;
    private static final int HEADER_SIZE;
    private static final int FLAGS_OFFSET;
    private static final int BUTTON_FLAGS_OFFSET;
    private static final int BUTTON_DATA_OFFSET;
    private static final int LAST_X_OFFSET;
    private static final int LAST_Y_OFFSET;
    private static final int[][] BUTTON_FLAGS;
    private final Memory inputBuffer = new Memory(256L);
    private final IntByReference inputSize = new IntByReference(256);
    private final AtomicInteger deltaX = new AtomicInteger();
    private final AtomicInteger deltaY = new AtomicInteger();
    private final AtomicInteger queuedEventCount = new AtomicInteger();
    private final ConcurrentLinkedQueue<Runnable> mainThreadEvents = new ConcurrentLinkedQueue();
    private final CountDownLatch startupFinished = new CountDownLatch(1);
    private final Object registrationLock = new Object();
    private final WinUser.WindowProc windowProc = this::windowProc;
    private volatile boolean running;
    private volatile boolean initialized;
    private volatile boolean rawRequested;
    private volatile boolean exclusive;
    private volatile boolean windowFocused;
    private volatile int cursorCenterX;
    private volatile int cursorCenterY;
    private volatile long glfwWindowHandle;
    private volatile WinDef.HWND inputWindow;
    private volatile String windowClassName;
    private MouseButtonSink buttonSink;
    private ScrollSink scrollSink;

    public boolean initialize(long windowHandle, MouseButtonSink buttons, ScrollSink scroll) {
        if (!WINDOWS || this.running) {
            return false;
        }
        this.glfwWindowHandle = windowHandle;
        this.buttonSink = buttons;
        this.scrollSink = scroll;
        this.running = true;
        Thread thread = new Thread(this::runMessageLoop, "VeloTune-Raw-Input-Buffer");
        thread.setDaemon(true);
        thread.start();
        try {
            if (!this.startupFinished.await(2L, TimeUnit.SECONDS) || !this.initialized) {
                this.close();
                return false;
            }
        }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            this.close();
            return false;
        }
        return true;
    }

    public boolean isRunning() {
        return this.running && this.initialized;
    }

    public boolean isCapturingRawInput() {
        return this.isRunning() && this.exclusive;
    }

    public void updateState(boolean focused, boolean useExclusiveInput, int centerX, int centerY) {
        this.windowFocused = focused;
        this.cursorCenterX = centerX;
        this.cursorCenterY = centerY;
        this.setExclusive(focused && useExclusiveInput);
        if (!focused) {
            this.clearPending();
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void setExclusive(boolean value) {
        if (!this.isRunning() || this.rawRequested == value && (value || !this.exclusive)) {
            return;
        }
        Object object = this.registrationLock;
        synchronized (object) {
            if (!this.isRunning() || this.rawRequested == value && (value || !this.exclusive)) {
                return;
            }
            this.clearPending();
            this.rawRequested = value;
            if (!value && this.exclusive) {
                if (this.registerMouse(256, this.inputWindow)) {
                    this.exclusive = false;
                } else {
                    VeloTuneManager.LOGGER.warn("Could not restore normal Windows mouse messages");
                }
            }
        }
    }

    public double pollDeltaX() {
        return this.deltaX.getAndSet(0);
    }

    public double pollDeltaY() {
        return this.deltaY.getAndSet(0);
    }

    public void flushEvents(boolean process) {
        Runnable event;
        while ((event = this.mainThreadEvents.poll()) != null) {
            this.queuedEventCount.decrementAndGet();
            if (!process) continue;
            event.run();
        }
    }

    public void clearPending() {
        this.deltaX.set(0);
        this.deltaY.set(0);
        this.flushEvents(false);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void runMessageLoop() {
        try {
            WinDef.HMODULE instance = Kernel32.INSTANCE.GetModuleHandle(null);
            this.windowClassName = "VeloTuneRawInput_" + Long.toUnsignedString(this.glfwWindowHandle);
            WinUser.WNDCLASSEX windowClass = new WinUser.WNDCLASSEX();
            windowClass.cbSize = windowClass.size();
            windowClass.lpfnWndProc = this.windowProc;
            windowClass.hInstance = instance;
            windowClass.lpszClassName = this.windowClassName;
            User32.INSTANCE.RegisterClassEx(windowClass);
            this.inputWindow = User32.INSTANCE.CreateWindowEx(0, this.windowClassName, "VeloTune Raw Input", 0, 0, 0, 0, 0, null, null, (WinDef.HINSTANCE)instance, null);
            if (this.inputWindow == null || !this.registerMouse(256, this.inputWindow)) {
                throw new IllegalStateException("Unable to create/register the WM_INPUT target");
            }
            this.initialized = true;
            this.startupFinished.countDown();
            WinUser.MSG message = new WinUser.MSG();
            while (this.running && User32.INSTANCE.GetMessage(message, null, 0, 0) > 0) {
                User32.INSTANCE.TranslateMessage(message);
                User32.INSTANCE.DispatchMessage(message);
            }
        }
        catch (Throwable error) {
            VeloTuneManager.LOGGER.warn("Windows raw input buffer unavailable; using normal GLFW input", error);
        }
        finally {
            this.initialized = false;
            this.running = false;
            this.startupFinished.countDown();
            this.destroyNativeWindow();
        }
    }

    private WinDef.LRESULT windowProc(WinDef.HWND hwnd, int message, WinDef.WPARAM wParam, WinDef.LPARAM lParam) {
        if (message == 255) {
            this.handleRawInput(lParam);
        }
        return User32.INSTANCE.DefWindowProc(hwnd, message, wParam, lParam);
    }

    private void handleRawInput(WinDef.LPARAM rawInputHandle) {
        if (!(this.running && this.rawRequested && this.windowFocused)) {
            return;
        }
        this.inputSize.setValue(256);
        WinNT.HANDLE handle = new WinNT.HANDLE(new Pointer(rawInputHandle.longValue()));
        int bytes = User32Ex.INSTANCE.GetRawInputData(handle, 0x10000003, (Pointer)this.inputBuffer, this.inputSize, HEADER_SIZE);
        if (bytes <= 0 || this.inputBuffer.getInt(0L) != 0) {
            return;
        }
        int flags = this.inputBuffer.getShort((long)FLAGS_OFFSET) & 0xFFFF;
        if ((flags & 1) == 0) {
            int x = this.inputBuffer.getInt((long)LAST_X_OFFSET);
            int y = this.inputBuffer.getInt((long)LAST_Y_OFFSET);
            if (!(this.exclusive || x == 0 && y == 0)) {
                this.activateRawCapture();
                return;
            }
            if (!this.exclusive) {
                return;
            }
            if (x != 0) {
                this.deltaX.addAndGet(x);
            }
            if (y != 0) {
                this.deltaY.addAndGet(y);
            }
            if (x != 0 || y != 0) {
                this.centerSystemCursor();
            }
        }
        if (!this.exclusive) {
            return;
        }
        int buttonFlags = this.inputBuffer.getShort((long)BUTTON_FLAGS_OFFSET) & 0xFFFF;
        if (buttonFlags == 0) {
            return;
        }
        this.handleButtons(buttonFlags);
        short buttonData = this.inputBuffer.getShort((long)BUTTON_DATA_OFFSET);
        if ((buttonFlags & 0x400) != 0) {
            this.enqueue(() -> this.scrollSink.accept(this.glfwWindowHandle, 0.0, (double)buttonData / 120.0));
        }
        if ((buttonFlags & 0x800) != 0) {
            this.enqueue(() -> this.scrollSink.accept(this.glfwWindowHandle, (double)buttonData / 120.0, 0.0));
        }
    }

    private void handleButtons(int flags) {
        for (int button = 0; button < BUTTON_FLAGS.length; ++button) {
            int currentButton = button;
            if ((flags & BUTTON_FLAGS[button][0]) != 0) {
                this.enqueue(() -> this.buttonSink.accept(this.glfwWindowHandle, currentButton, 1, 0));
            }
            if ((flags & BUTTON_FLAGS[button][1]) == 0) continue;
            this.enqueue(() -> this.buttonSink.accept(this.glfwWindowHandle, currentButton, 0, 0));
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void activateRawCapture() {
        Object object = this.registrationLock;
        synchronized (object) {
            if (!this.isRunning() || !this.rawRequested || this.exclusive) {
                return;
            }
            this.clearPending();
            if (this.registerMouse(304, this.inputWindow)) {
                this.exclusive = true;
                VeloTuneManager.LOGGER.info("Windows raw mouse capture confirmed and activated");
            } else {
                this.rawRequested = false;
                VeloTuneManager.LOGGER.warn("Raw mouse packets were detected, but legacy input could not be disabled; keeping GLFW input");
            }
        }
    }

    private void enqueue(Runnable event) {
        int count = this.queuedEventCount.incrementAndGet();
        if (count > 1024) {
            this.queuedEventCount.decrementAndGet();
            return;
        }
        this.mainThreadEvents.add(event);
    }

    private void centerSystemCursor() {
        int x = this.cursorCenterX;
        int y = this.cursorCenterY;
        if (x != 0 || y != 0) {
            User32.INSTANCE.SetCursorPos((long)x, (long)y);
        }
    }

    private boolean registerMouse(int flags, WinDef.HWND target) {
        RawInputDevice[] devices = (RawInputDevice[])new RawInputDevice().toArray(1);
        devices[0].usUsagePage = 1;
        devices[0].usUsage = (short)2;
        devices[0].dwFlags = flags;
        devices[0].hwndTarget = target;
        devices[0].write();
        return User32Ex.INSTANCE.RegisterRawInputDevices(devices, 1, devices[0].size());
    }

    private void destroyNativeWindow() {
        String className;
        WinDef.HWND window = this.inputWindow;
        this.inputWindow = null;
        if (window != null) {
            try {
                this.registerMouse(1, null);
                User32.INSTANCE.DestroyWindow(window);
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
        if ((className = this.windowClassName) != null) {
            try {
                User32.INSTANCE.UnregisterClass(className, (WinDef.HINSTANCE)Kernel32.INSTANCE.GetModuleHandle(null));
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
    }

    @Override
    public void close() {
        if (!WINDOWS || !this.running) {
            return;
        }
        this.running = false;
        this.rawRequested = false;
        this.exclusive = false;
        this.clearPending();
        WinDef.HWND window = this.inputWindow;
        if (window != null) {
            try {
                this.registerMouse(1, null);
                User32.INSTANCE.PostMessage(window, 16, new WinDef.WPARAM(0L), new WinDef.LPARAM(0L));
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
    }

    static {
        FLAGS_OFFSET = HEADER_SIZE = 8 + 2 * Native.POINTER_SIZE;
        BUTTON_FLAGS_OFFSET = HEADER_SIZE + 4;
        BUTTON_DATA_OFFSET = HEADER_SIZE + 6;
        LAST_X_OFFSET = HEADER_SIZE + 12;
        LAST_Y_OFFSET = HEADER_SIZE + 16;
        BUTTON_FLAGS = new int[][]{{1, 2}, {4, 8}, {16, 32}, {64, 128}, {256, 512}};
    }

    @FunctionalInterface
    @Environment(value=EnvType.CLIENT)
    public static interface MouseButtonSink {
        public void accept(long var1, int var3, int var4, int var5);
    }

    @FunctionalInterface
    @Environment(value=EnvType.CLIENT)
    public static interface ScrollSink {
        public void accept(long var1, double var3, double var5);
    }

    @Environment(value=EnvType.CLIENT)
    private static interface User32Ex
    extends StdCallLibrary {
        public static final User32Ex INSTANCE = (User32Ex)Native.load((String)"user32", User32Ex.class);

        public boolean RegisterRawInputDevices(RawInputDevice[] var1, int var2, int var3);

        public int GetRawInputData(WinNT.HANDLE var1, int var2, Pointer var3, IntByReference var4, int var5);
    }

    @Structure.FieldOrder(value={"usUsagePage", "usUsage", "dwFlags", "hwndTarget"})
    @Environment(value=EnvType.CLIENT)
    public static final class RawInputDevice
    extends Structure {
        public short usUsagePage;
        public short usUsage;
        public int dwFlags;
        public WinDef.HWND hwndTarget;
    }
}
