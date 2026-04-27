package gg.openmod.features;

import java.util.UUID;

public record FriendStatusChangedEvent(UUID uuid, boolean online) {}
