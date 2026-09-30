// Smoke-тест NightixVocal.dll: грузит DLL, запускает loopback, снимает уровень.
//
// Нужен, чтобы проверить, что WASAPI инициализируется и поток захвата не падает,
// ещё до того как DLL поедет внутри клиента.
//   build_test.ps1 && test_vocal.exe

#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <cstdio>

typedef int  (*FnStart)(const char*);
typedef void (*FnStop)(void);
typedef float(*FnLevel)(void);
typedef float(*FnPeak)(void);
typedef int  (*FnFrames)(void);
typedef int  (*FnFlags)(void);
typedef int  (*FnVersion)(void);

int main(int argc, char** argv) {
    const char* path = argc > 1 ? argv[1] : "NightixVocal.dll";

    HMODULE dll = LoadLibraryA(path);
    if (dll == nullptr) {
        std::printf("FAIL: LoadLibrary(%s) error=%lu\n", path, GetLastError());
        return 1;
    }

    FnStart    pStart    = (FnStart)   GetProcAddress(dll, "VocalStart");
    FnStop     pStop     = (FnStop)    GetProcAddress(dll, "VocalStop");
    FnLevel    pLevel    = (FnLevel)   GetProcAddress(dll, "VocalLevel");
    FnPeak     pPeak     = (FnPeak)    GetProcAddress(dll, "VocalPeak");
    FnFrames   pFrames   = (FnFrames)  GetProcAddress(dll, "VocalFrames");
    FnFlags    pFlags    = (FnFlags)   GetProcAddress(dll, "VocalFlags");
    FnVersion  pVersion  = (FnVersion) GetProcAddress(dll, "VocalApiVersion");

    if (!pStart || !pStop || !pLevel || !pPeak || !pFrames || !pFlags || !pVersion) {
        std::printf("FAIL: не все экспорты найдены\n");
        FreeLibrary(dll);
        return 1;
    }

    std::printf("apiVersion=%d\n", pVersion());

    const int started = pStart(nullptr);
    std::printf("VocalStart -> %d\n", started);
    if (started == 0) {
        std::printf("FAIL: loopback не открылся (нет render-эндпоинта?)\n");
        FreeLibrary(dll);
        return 1;
    }

    float maxLevel = 0.0f;
    for (int i = 0; i < 20; ++i) {
        Sleep(250);
        const float lvl = pLevel();
        const float pk  = pPeak();
        const int   fr  = pFrames();
        const int   fl  = pFlags();
        if (lvl > maxLevel) maxLevel = lvl;
        std::printf("  t=%5dms level=%.4f peak=%.4f frames=%d flags=0x%x\n",
                    (i + 1) * 250, lvl, pk, fr, fl);
    }

    pStop();
    std::printf("VocalStop ok, level after stop = %.2f (ожидается -1)\n", pLevel());
    FreeLibrary(dll);

    std::printf("%s\n", maxLevel > 0.0f ? "OK: звук ловится" : "OK: поток жив (тишина в системе — уровень 0)");
    return 0;
}
