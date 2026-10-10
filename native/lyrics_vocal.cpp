// DecideVocal.dll — детектор вокала в реальном времени.
//
// Ловит звук с выхода системы (WASAPI loopback от дефолтного render-эндпоинта),
// выделяет полосу 180–4200 Гц (вокальный диапазон) и отдаёт сглаженную энергию 0..1.
// Энергия нужна модулю Lyrics Text, чтобы «разогревать» глифы на словах, которые
// сейчас поются.
//
// Интерфейс — плоский C, клиент подключает через JNA (как OptMedia.dll из Kimiko).
// Никакой привязки к конкретному Java-классу, чтобы DLL можно было грузить и из других
// мест.
//
// Захват поллинговый (без event-callback): IAudioClient в этом SDK не отдаёт
// GetEventHandle, а поллинг с буфером 10 мс и паузой 4 мс жрёт меньше процента CPU
// и работает на любой версии Windows.
//
// Сборка: build_native.ps1 (MSVC x64, статический CRT, без манифеста).

#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <audioclient.h>
#include <mmdeviceapi.h>
#include <ks.h>
#include <ksmedia.h>

#include <atomic>
#include <cmath>
#include <cstring>
#include <cwchar>

// Нет в доступных заголовках SDK — HRESULT из audioclient.h, Windows 8+.
static const HRESULT kBufferEmpty = static_cast<HRESULT>(0x08890001u);

namespace {

template <typename T>
inline T clamp01(T v) {
    return v < static_cast<T>(0) ? static_cast<T>(0)
         : (v > static_cast<T>(1) ? static_cast<T>(1) : v);
}

constexpr int kSampleRate    = 48000;
constexpr int kChannels      = 2;
constexpr int kBlockAlign    = kChannels * static_cast<int>(sizeof(float));
constexpr int kBitsPerSample = 32;

// Полоса вокала. Ниже 180 Гц уходят бас-гитара и барабанный кик, выше 4.2 кГц
// начинается скрипка инструментов и «свист» вокала на открытых гласных.
constexpr double kBandHigh = 4200.0;

// Сглаживание огибающей. Атака быстрая (слово только что прозвучало — ждать нечего),
// спад медленный (иначе буквы мерцают между слогами).
constexpr float kAttack  = 0.35f;
constexpr float kRelease = 0.06f;

// Порог «есть сигнал вообще»: тишина не должна выглядеть как вокал.
constexpr float kNoiseFloor = 0.0015f;

// Доля энергии вокала в общей энергии. У инструментала (бас + ударные) вокала почти
// нет, даже когда общая громкость большая — поэтому нужен именно этот фильтр.
constexpr float kVocalRatio = 0.30f;

// Chamberlin state-variable filter: устойчивый band-pass из одного полю pair,
// не требует пересчёта коэффициентов под каждый сэмпл.
struct SVF {
    double low  = 0.0;
    double band = 0.0;

    inline double process(double input, double cutoffHz, double q) {
        const double f = 2.0 * sin(3.14159265358979323846 * cutoffHz / kSampleRate);
        const double damp = 1.0 / q;
        const double high = input - low - damp * band;
        band += f * high;
        low  += f * band;
        return band;
    }
};

// Усреднение по окну: счётчик + сумма, деление в конце окна.
class Envelope {
public:
    void add(double value) {
        sum_ += value;
        ++count_;
    }

    float flush() {
        const float avg = count_ > 0 ? static_cast<float>(sum_ / count_) : 0.0f;
        sum_ = 0.0;
        count_ = 0;
        return avg;
    }

    bool empty() const { return count_ == 0; }

private:
    double sum_ = 0.0;
    int    count_ = 0;
};

constexpr int kFlagRunning  = 1 << 0;
constexpr int kFlagEndpoint = 1 << 1;
constexpr int kFlagSilent   = 1 << 2;

struct State {
    IMMDeviceEnumerator* enumerator = nullptr;
    IMMDevice*           device     = nullptr;
    IAudioClient*        client     = nullptr;
    IAudioCaptureClient* capture    = nullptr;
    ISimpleAudioVolume*  volume     = nullptr;
    HANDLE               thread     = nullptr;

    std::atomic<bool>      running{false};
    std::atomic<int>       flags{0};
    std::atomic<long long> frames{0};
    std::atomic<float>     level{0.0f};
    std::atomic<float>     peak{0.0f};
    std::atomic<int>       generation{0};
};

State g;

void setSilent(bool silent) {
    int f = g.flags.load(std::memory_order_relaxed);
    if (silent) f |= kFlagSilent; else f &= ~kFlagSilent;
    g.flags.store(f, std::memory_order_relaxed);
}

// Переводит громкость сессии в mute, чтобы заглушить источник по имени exe.
bool findProcessVolume(const wchar_t* processName, ISimpleAudioVolume** out) {
    if (processName == nullptr || *processName == 0) return false;

    IMMDeviceCollection* sessions = nullptr;
    if (FAILED(g.enumerator->EnumAudioEndpoints(eRender, DEVICE_STATE_ACTIVE, &sessions))
        || sessions == nullptr) {
        return false;
    }

    UINT count = 0;
    if (FAILED(sessions->GetCount(&count)) || count == 0) {
        sessions->Release();
        return false;
    }

    bool found = false;
    for (UINT i = 0; i < count && !found; ++i) {
        IMMDevice* dev = nullptr;
        if (FAILED(sessions->Item(i, &dev)) || dev == nullptr) continue;

        LPWSTR id = nullptr;
        if (SUCCEEDED(dev->GetId(&id)) && id != nullptr) {
            // ID эндпоинта вида {pid}.{guid} — pid идёт первым.
            wchar_t* end = nullptr;
            const long parsed = wcstol(id + 1, &end, 10);
            if (end != nullptr && *end == L'.') {
                const DWORD pid = static_cast<DWORD>(parsed);

                HANDLE process = OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, FALSE, pid);
                if (process != nullptr) {
                    wchar_t exePath[MAX_PATH] = L"";
                    DWORD size = MAX_PATH;
                    const bool gotName =
                        QueryFullProcessImageNameW(process, 0, exePath, &size) != 0 && exePath[0] != 0;
                    CloseHandle(process);

                    if (gotName) {
                        const wchar_t* slash = wcsrchr(exePath, L'\\');
                        const wchar_t* name = slash ? slash + 1 : exePath;
                        if (_wcsicmp(name, processName) == 0) {
                            if (SUCCEEDED(dev->Activate(
                                    __uuidof(ISimpleAudioVolume), CLSCTX_INPROC_SERVER,
                                    nullptr, reinterpret_cast<void**>(out)))) {
                                found = true;
                            }
                        }
                    }
                }
            }
            CoTaskMemFree(id);
        }
        dev->Release();
    }

    sessions->Release();
    return found;
}

DWORD WINAPI captureThread(LPVOID param) {
    auto* myGeneration = static_cast<int*>(param);
    const int generation = *myGeneration;
    delete myGeneration;

    SVF      vocalFilter;
    Envelope vocalEnv;
    Envelope fullEnv;
    bool     wasSilent = true;

    while (g.generation.load(std::memory_order_acquire) == generation
        && g.running.load(std::memory_order_acquire)) {

        bool readAny = false;
        for (;;) {
            BYTE*   data         = nullptr;
            DWORD   flags        = 0;
            UINT32  framesGot    = 0;
            UINT64  pts          = 0;
            UINT64  devicePeriod = 0;

            const HRESULT hr = g.capture->GetBuffer(
                &data, &framesGot, &flags, &pts, &devicePeriod);
            if (hr == kBufferEmpty || framesGot == 0) break;
            if (FAILED(hr)) {
                setSilent(true);
                return 0;
            }

            if (flags & AUDCLNT_BUFFERFLAGS_SILENT) {
                std::memset(data, 0, static_cast<size_t>(framesGot) * kBlockAlign);
            } else {
                const float* samples = reinterpret_cast<const float*>(data);
                for (UINT32 i = 0; i < framesGot; ++i) {
                    const float left  = samples[i * kChannels];
                    const float right = samples[i * kChannels + 1];
                    const float mono  = (left + right) * 0.5f;

                    const double v = vocalFilter.process(mono, kBandHigh, 0.9);
                    vocalEnv.add(v * v);
                    fullEnv.add(static_cast<double>(mono) * mono);
                }
                g.frames.fetch_add(framesGot, std::memory_order_relaxed);
            }

            g.capture->ReleaseBuffer(framesGot);
            readAny = true;
        }

        if (!vocalEnv.empty()) {
            const float vocalRms = std::sqrt(vocalEnv.flush());
            const float overall  = std::sqrt(fullEnv.flush());
            g.peak.store(vocalRms, std::memory_order_relaxed);

            // Сколько вокала сверх порога, нормализованное в 0..1.
            float value = 0.0f;
            if (overall > kNoiseFloor) {
                const float ratio = vocalRms / overall;
                if (ratio > kVocalRatio) {
                    const float excess   = (ratio - kVocalRatio) / (1.0f - kVocalRatio);
                    const float loudness = vocalRms / (vocalRms + 0.35f);
                    value = clamp01(excess * loudness * 3.0f);
                }
            }

            float current = g.level.load(std::memory_order_relaxed);
            current += (value - current) * ((value > current) ? kAttack : kRelease);
            if (current < 0.0005f) current = 0.0f;
            g.level.store(current, std::memory_order_relaxed);

            const bool silent = current <= 0.0005f;
            if (silent != wasSilent) {
                wasSilent = silent;
                setSilent(silent);
            }
        } else if (!readAny) {
            // Ничего не пришло — считаем, что сигнала нет, и не жуём CPU зря.
            Sleep(4);
        }
    }

    g.level.store(0.0f, std::memory_order_relaxed);
    g.peak.store(0.0f, std::memory_order_relaxed);
    setSilent(true);
    return 0;
}

void releaseAll() {
    g.running.store(false, std::memory_order_release);
    g.generation.fetch_add(1, std::memory_order_acq_rel);

    if (g.thread != nullptr) {
        // Поток замечает смену generation и выходит сам; ждём, чтобы не освобождать
        // интерфейсы под ним.
        WaitForSingleObject(g.thread, 2000);
        CloseHandle(g.thread);
        g.thread = nullptr;
    }
    if (g.capture != nullptr) { g.capture->Release(); g.capture = nullptr; }
    if (g.client != nullptr) {
        g.client->Stop();
        g.client->Release();
        g.client = nullptr;
    }
    if (g.volume != nullptr) { g.volume->Release(); g.volume = nullptr; }
    if (g.device != nullptr) { g.device->Release(); g.device = nullptr; }
    if (g.enumerator != nullptr) { g.enumerator->Release(); g.enumerator = nullptr; }

    g.flags.store(0, std::memory_order_relaxed);
    g.frames.store(0, std::memory_order_relaxed);
    g.level.store(0.0f, std::memory_order_relaxed);
    g.peak.store(0.0f, std::memory_order_relaxed);
}

}  // namespace

extern "C" {

__declspec(dllexport) int VocalStart(const char* processName) {
    if (g.running.load(std::memory_order_acquire)) return 1;

    CoInitializeEx(nullptr, COINIT_MULTITHREADED);

    if (FAILED(CoCreateInstance(
            __uuidof(MMDeviceEnumerator), nullptr, CLSCTX_INPROC_SERVER,
            __uuidof(IMMDeviceEnumerator),
            reinterpret_cast<void**>(&g.enumerator)))) {
        return 0;
    }

    if (FAILED(g.enumerator->GetDefaultAudioEndpoint(eRender, ERole::eConsole, &g.device))
        || g.device == nullptr) {
        releaseAll();
        CoUninitialize();
        return 0;
    }

    if (FAILED(g.device->Activate(
            __uuidof(IAudioClient), CLSCTX_INPROC_SERVER, nullptr,
            reinterpret_cast<void**>(&g.client)))) {
        releaseAll();
        CoUninitialize();
        return 0;
    }

    // В этом SDK WAVEFORMATEXTENSIBLE — обёртка над WAVEFORMATEX, полей плоских нет.
    WAVEFORMATEXTENSIBLE format{};
    format.Format.wFormatTag       = WAVE_FORMAT_EXTENSIBLE;
    format.Format.nChannels        = kChannels;
    format.Format.nSamplesPerSec   = kSampleRate;
    format.Format.wBitsPerSample   = kBitsPerSample;
    format.Format.nBlockAlign      = kBlockAlign;
    format.Format.nAvgBytesPerSec  = kSampleRate * kBlockAlign;
    format.Format.cbSize           = sizeof(WAVEFORMATEXTENSIBLE) - sizeof(WAVEFORMATEX);
    format.Samples.wValidBitsPerSample = kBitsPerSample;
    format.dwChannelMask = SPEAKER_FRONT_LEFT | SPEAKER_FRONT_RIGHT;
    format.SubFormat     = KSDATAFORMAT_SUBTYPE_IEEE_FLOAT;

    // Shared mode + loopback: движок сам приведёт формат устройства к нашему.
    // Initialize принимает только WAVEFORMATEX*, но WAVEFORMATEXTENSIBLE начинается с
    // него, поэтому каст валиден и расширенные поля (маска каналов, SubFormat) видны.
    const DWORD streamFlags = AUDCLNT_STREAMFLAGS_LOOPBACK;
    const HRESULT hrInit = g.client->Initialize(
        AUDCLNT_SHAREMODE_SHARED, streamFlags, 0, 0,
        reinterpret_cast<const WAVEFORMATEX*>(&format), nullptr);
    if (FAILED(hrInit)) {
        releaseAll();
        CoUninitialize();
        return 0;
    }

    if (FAILED(g.client->GetService(__uuidof(IAudioCaptureClient),
                                  reinterpret_cast<void**>(&g.capture)))) {
        releaseAll();
        CoUninitialize();
        return 0;
    }

    if (FAILED(g.client->Start())) {
        releaseAll();
        CoUninitialize();
        return 0;
    }

    // Заглушить процесс, если он найден: тишина в плеере = тишина вокала.
    if (processName != nullptr && processName[0] != 0) {
        wchar_t wide[260];
        if (MultiByteToWideChar(CP_UTF8, 0, processName, -1, wide, 260) > 0) {
            ISimpleAudioVolume* volume = nullptr;
            if (findProcessVolume(wide, &volume) && volume != nullptr) {
                volume->SetMute(TRUE, nullptr);
                g.volume = volume;
            }
        }
    }

    g.flags.store(kFlagEndpoint, std::memory_order_relaxed);
    g.running.store(true, std::memory_order_release);
    setSilent(true);

    g.generation.fetch_add(1, std::memory_order_acq_rel);
    auto* gen = new int(g.generation.load(std::memory_order_acquire));
    g.thread = CreateThread(nullptr, 0, captureThread, gen, 0, nullptr);
    if (g.thread == nullptr) {
        delete gen;
        releaseAll();
        CoUninitialize();
        return 0;
    }

    g.flags.fetch_or(kFlagRunning, std::memory_order_relaxed);
    return 1;
}

__declspec(dllexport) void VocalStop(void) {
    releaseAll();
    CoUninitialize();
}

__declspec(dllexport) void VocalDeinitialize(void) {
    releaseAll();
}

__declspec(dllexport) float VocalLevel(void) {
    if (!g.running.load(std::memory_order_acquire)) return -1.0f;
    return g.level.load(std::memory_order_relaxed);
}

__declspec(dllexport) float VocalPeak(void) {
    if (!g.running.load(std::memory_order_acquire)) return 0.0f;
    return g.peak.load(std::memory_order_relaxed);
}

__declspec(dllexport) int VocalFrames(void) {
    if (!g.running.load(std::memory_order_acquire)) return -1;
    return static_cast<int>(g.frames.load(std::memory_order_relaxed) & 0x7fffffff);
}

__declspec(dllexport) int VocalFlags(void) {
    return g.flags.load(std::memory_order_relaxed);
}

__declspec(dllexport) int VocalApiVersion(void) {
    return 1;
}

}  // extern "C"

BOOL APIENTRY DllMain(HMODULE, DWORD, LPVOID) {
    return TRUE;
}
