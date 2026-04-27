package gg.openmod.features;

/**
 * Arkadaş listesi değiştiğinde (ekleme, silme, istek gelme/gitmesi)
 * EventBus üzerinden fırlatılır.
 *
 * FriendsScreen bu event'i dinleyip refreshList() çağırır —
 * relay cevabı gelmeden önce ekranı boşaltma sorununu çözer.
 */
public record FriendListChangedEvent() {}
