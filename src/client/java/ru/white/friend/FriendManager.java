package ru.white.friend;

import com.google.gson.*;
import ru.white.Client;
import ru.white.manager.event_impl.WorldLoadEvent;
import ru.white.manager.events.orbit.EventHandler;


import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class FriendManager {
    private static final Path FILE = Path.of("C:/wvisual/client1_21_11/friends.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Set<String> friends = new HashSet<>();
    
    public void init() {
        load();
        Client.eventHandler().subscribe(this);
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        load();
    }
    
    public boolean add(String name) {
        boolean added = friends.add(name.toLowerCase());
        if (added) save();
        return added;
    }

    
    public boolean remove(String name) {
        boolean removed = friends.remove(name.toLowerCase());
        if (removed) save();
        return removed;
    }

    public boolean isFriend(String name) {
        return friends.contains(name.toLowerCase());
    }

    public Set<String> getFriends() {
        return Collections.unmodifiableSet(friends);
    }

    
    private void save() {
        JsonArray arr = new JsonArray();
        friends.forEach(arr::add);
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer w = new OutputStreamWriter(new FileOutputStream(FILE.toFile()), StandardCharsets.UTF_8)) {
                GSON.toJson(arr, w);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private void load() {
        if (!Files.exists(FILE)) return;
        try (Reader r = new InputStreamReader(new FileInputStream(FILE.toFile()), StandardCharsets.UTF_8)) {
            JsonArray arr = JsonParser.parseReader(r).getAsJsonArray();
            friends.clear();
            arr.forEach(el -> friends.add(el.getAsString().toLowerCase()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
