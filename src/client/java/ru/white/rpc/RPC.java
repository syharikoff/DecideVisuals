package ru.white.rpc;


import ru.white.utils.annotation.IMinecraft;


import ru.white.utils.math.ServerUtil;

public class RPC implements IMinecraft {

    public static DiscordRichPresence presence = new DiscordRichPresence();
    public static boolean started;
    private static Thread thread;

    
    public void startRpc() {
        // Проверяем, доступна ли библиотека Discord RPC
        if (!DiscordRPC.Loader.isAvailable()) {
            return;
        }

        DiscordRPC rpc = DiscordRPC.Loader.getInstance();
        if (!started) {
            started = true;
            DiscordEventHandlers handlers = new DiscordEventHandlers();
            rpc.Discord_Initialize("1515740176549416970", handlers, true, "");
            presence.startTimestamp = (System.currentTimeMillis() / 1000L);
            presence.largeImageText = "1.21.11";
            rpc.Discord_UpdatePresence(presence);

            thread = new Thread(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    rpc.Discord_RunCallbacks();
                    String serverIp = "В главном меню";
                    if (mc.world != null) {
                        if (mc.getNetworkHandler() != null && mc.getNetworkHandler().getServerInfo() != null) {
                            serverIp = mc.getNetworkHandler().getServerInfo().address;
                        } else {
                            serverIp = "Одиночный мир";
                        }
                    }
                    presence.details = "1.21.11";
                    presence.state = serverIp;

                    presence.button_label_1 = "Download";
                    presence.button_url_1 = "https://DecideVisuals.fun";

                    presence.button_label_2 = "Telegram";
                    presence.button_url_2 = "https://t.me/DecideVisuals";


                    presence.largeImageKey = "9809f2b0af4a1126b606ad55158d6dd74e608f5984f52bd7ed551f12e9741c55";
                    presence.largeImageText = "DecideVisuals";
                    //presence.smallImageKey = Profile.getAvatarUrl();
                    //presence.smallImageText = Profile.getUsername() + " | " + Profile.getUid();

                    rpc.Discord_UpdatePresence(presence);
                    try {
                        Thread.sleep(2000L);
                    } catch (InterruptedException ignored) {
                    }
                }
            }, "TH-RPC-Handler");
            thread.start();

        }
    }
}
