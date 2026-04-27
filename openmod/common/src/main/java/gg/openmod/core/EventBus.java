package gg.openmod.core;

import gg.openmod.OpenMod;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * OpenMod iç event sistemi.
 * Özellikler:
 *  - Thread-safe: CopyOnWriteArrayList, abonelikler herhangi bir thread'den eklenebilir
 *  - Subscription handle: unsubscribe için referans döndürür
 *  - Exception izolasyonu: bir listener'ın hatası diğerlerini etkilemez
 *
 * Kullanım:
 *   var cancel = bus.subscribe(FriendStatusEvent.class, e -> updateHud(e));
 *   // Daha sonra:
 *   cancel.run();
 */
public class EventBus {

    private final Map<Class<?>, CopyOnWriteArrayList<Subscription<?>>> listeners = new ConcurrentHashMap<>();

    /**
     * Bir event tipine abone ol.
     * @return Aboneliği iptal etmek için çağrılacak Runnable
     */
    @SuppressWarnings("unchecked")
    public <T> Runnable subscribe(Class<T> eventType, Consumer<T> listener) {
        Subscription<T> sub = new Subscription<>(listener);
        listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(sub);
        return () -> {
            CopyOnWriteArrayList<Subscription<?>> subs = listeners.get(eventType);
            if (subs != null) subs.remove(sub);
        };
    }

    /**
     * Tüm dinleyicilere event'i ilet.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T> void fire(T event) {
        CopyOnWriteArrayList<Subscription<?>> subs = listeners.get(event.getClass());
        if (subs == null || subs.isEmpty()) return;

        for (Subscription sub : subs) {
            try {
                sub.accept(event);
            } catch (Exception e) {
                OpenMod.LOGGER.error("[EventBus] Listener exception for {}: {}",
                        event.getClass().getSimpleName(), e.getMessage(), e);
            }
        }
    }

    /** Belirli bir event tipinin tüm dinleyicilerini kaldır. */
    public <T> void clearListeners(Class<T> eventType) {
        listeners.remove(eventType);
    }

    /** Tüm dinleyicileri temizle (genellikle shutdown sırasında). */
    public void clearAll() {
        listeners.clear();
    }

    // ------------------------------------------------------------------ //

    private static final class Subscription<T> {
        private final Consumer<T> listener;
        Subscription(Consumer<T> listener) { this.listener = listener; }
        void accept(T event) { listener.accept(event); }
    }
}
