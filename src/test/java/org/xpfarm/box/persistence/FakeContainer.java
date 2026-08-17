/*
 * The Box - a rare nocturnal stalker that moves only while unobserved and feeds on the
 * experience of whoever watches it.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.box.persistence;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Minimal map-backed {@link PersistentDataContainer} double for headless codec tests.
 *
 * <p>Only the read/write methods {@link BoxCodec} actually touches are functional; the
 * live-server byte serialization and adapter-context methods throw. This mirrors the
 * proven approach in the sibling redstone-train plugin's {@code TrainCodecTest}, letting
 * {@link BoxCodec} round-trip a {@code BoxState} without a running server. What CANNOT be
 * exercised here — and is deferred to gate 7a runtime verification — is the real
 * {@code CraftPersistentDataContainer} on a spawned entity and its survival across a
 * chunk save/load and full server restart.
 */
final class FakeContainer implements PersistentDataContainer {

    private final Map<NamespacedKey, Object> data = new HashMap<>();

    @Override
    public <P, C> void set(NamespacedKey key, PersistentDataType<P, C> type, C value) {
        data.put(key, value);
    }

    @Override
    public <P, C> boolean has(NamespacedKey key, PersistentDataType<P, C> type) {
        Object value = data.get(key);
        return value != null && type.getComplexType().isInstance(value);
    }

    @Override
    public boolean has(NamespacedKey key) {
        return data.containsKey(key);
    }

    @Override
    public <P, C> C get(NamespacedKey key, PersistentDataType<P, C> type) {
        Object value = data.get(key);
        return type.getComplexType().isInstance(value) ? type.getComplexType().cast(value) : null;
    }

    @Override
    public <P, C> C getOrDefault(NamespacedKey key, PersistentDataType<P, C> type, C defaultValue) {
        C value = get(key, type);
        return value != null ? value : defaultValue;
    }

    @Override
    public Set<NamespacedKey> getKeys() {
        return Set.copyOf(data.keySet());
    }

    @Override
    public void remove(NamespacedKey key) {
        data.remove(key);
    }

    @Override
    public boolean isEmpty() {
        return data.isEmpty();
    }

    @Override
    public int getSize() {
        return data.size();
    }

    @Override
    public void copyTo(PersistentDataContainer other, boolean replace) {
        throw new UnsupportedOperationException("Not needed by tests");
    }

    @Override
    public PersistentDataAdapterContext getAdapterContext() {
        throw new UnsupportedOperationException("Not needed by tests");
    }

    @Override
    public byte[] serializeToBytes() {
        throw new UnsupportedOperationException("Not needed by tests");
    }

    @Override
    public void readFromBytes(byte[] bytes, boolean clear) {
        throw new UnsupportedOperationException("Not needed by tests");
    }
}
