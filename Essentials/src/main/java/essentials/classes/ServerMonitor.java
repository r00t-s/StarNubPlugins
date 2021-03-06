package essentials.classes;

import starnubserver.StarNub;
import starnubserver.StarNubTask;
import starnubserver.events.events.StarNubEvent;
import starnubserver.events.starnub.StarNubEventHandler;
import starnubserver.events.starnub.StarNubEventSubscription;
import starnubserver.resources.files.PluginConfiguration;
import starnubserver.servers.starbound.StarboundServer;
import utilities.cache.objects.BooleanCache;
import utilities.cache.objects.TimeCache;
import utilities.events.Priority;
import utilities.file.simplejson.parser.ParseException;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class ServerMonitor {

    final PluginConfiguration CONFIG;

    private final StarNubEventSubscription PLAYER_JOIN_CRASH_HANDLER;
    private final CrashedHandler CRASH_HANDLER;
    private final StarNubEventSubscription STARNUB_STARTED;
    private final StarNubTask PROCESS_CHECK;
    private final StarNubTask QUERY_CHECK;

    public ServerMonitor(PluginConfiguration CONFIG) {
        this.CONFIG = CONFIG;
        CRASH_HANDLER = new CrashedHandler(CONFIG);
        PLAYER_JOIN_CRASH_HANDLER = new StarNubEventSubscription("Essentials", Priority.MEDIUM, "Player_Connected", CRASH_HANDLER);


        if ((boolean) CONFIG.getNestedValue("monitor", "start_on_load")) {
            this.STARNUB_STARTED = startStarnubStartedListener();
        } else {
            STARNUB_STARTED = null;
        }

        int interval = (int) CONFIG.getNestedValue("monitor", "interval");
        boolean processCheck = (boolean) CONFIG.getNestedValue("monitor", "process_crash");
        if (processCheck) {
            PROCESS_CHECK = processCheck(interval);
        } else {
            PROCESS_CHECK = null;
        }

        boolean responsiveness = (boolean) CONFIG.getNestedValue("monitor", "responsiveness", "tcp_query");
        if (responsiveness) {
            int tries = (int) CONFIG.getNestedValue("monitor", "responsiveness", "tries");
            QUERY_CHECK = responsiveness(interval, tries);
        } else {
            QUERY_CHECK = null;
        }
    }

    private StarNubEventSubscription startStarnubStartedListener() {
        return new StarNubEventSubscription("Essentials", Priority.MEDIUM, "StarNub_Startup_Complete", new StarNubEventHandler() {
            @Override
            public void onEvent(StarNubEvent starNubEvent) {
                startServer();
            }
        });
    }

    private StarNubTask processCheck(int interval) {
        return new StarNubTask("Essentials", "Essentials - Starbound Process Check", true, interval, interval, TimeUnit.SECONDS, () -> {
            StarboundServer starboundServer = StarNub.getStarboundServer();
            boolean alive = starboundServer.isAlive();
            if (!alive) {
                startServer();
                serverCrash();
            }
        });
    }

    private StarNubTask responsiveness(int interval, int tries) {
        return new StarNubTask("Essentials", "Essentials - Starbound Process Check", true, interval + 2, interval + 2, TimeUnit.SECONDS, () -> {
            StarboundServer starboundServer = StarNub.getStarboundServer();
            boolean responsive = starboundServer.isResponsive(tries);
            if (!responsive) {
                startServer();
                serverCrash();
            }
        });
    }

    private void startServer() {
        try {
            StarNub.getStarboundServer().start();
        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }
    }

    private void serverCrash() {
        ConcurrentHashMap<UUID, TimeCache> cacheMap = CRASH_HANDLER.getPLAYER_UUID_CACHE().getCACHE_MAP();
        for (TimeCache timeCache : cacheMap.values()) {
            BooleanCache booleanCache = (BooleanCache) timeCache;
            booleanCache.setBool(true);
        }
        new StarNubTask("Essentials", "Essentials - Server Crash - Cache Clear", 120, TimeUnit.SECONDS, () -> {
            for (Map.Entry entry : cacheMap.entrySet()) {
                UUID key = (UUID) entry.getKey();
                BooleanCache booleanCache = (BooleanCache) entry.getValue();
                if (booleanCache.isBool()) {
                    cacheMap.remove(key);
                }
            }
        });
    }

    public StarNubEventSubscription getPLAYER_JOIN_CRASH_HANDLER() {
        return PLAYER_JOIN_CRASH_HANDLER;
    }

    public StarNubEventSubscription getSTARNUB_STARTED() {
        return STARNUB_STARTED;
    }

    public StarNubTask getPROCESS_CHECK() {
        return PROCESS_CHECK;
    }

    public StarNubTask getQUERY_CHECK() {
        return QUERY_CHECK;
    }

    public void unregisterEventsTask() {
        PLAYER_JOIN_CRASH_HANDLER.removeRegistration();
        STARNUB_STARTED.removeRegistration();
        PROCESS_CHECK.removeTask();
        QUERY_CHECK.removeTask();
    }
}
