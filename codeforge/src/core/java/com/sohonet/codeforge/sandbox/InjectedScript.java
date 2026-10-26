package com.sohonet.codeforge.sandbox;

import net.minecraft.client.MinecraftClient;

public interface InjectedScript {
    void run(MinecraftClient client) throws Exception;
}