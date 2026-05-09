package com.slize.datarium.mixin.accessors;

import net.minecraft.client.resources.AbstractResourcePack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.io.IOException;
import java.io.InputStream;

@Mixin(AbstractResourcePack.class)
public interface IAbstractResourcePackAccessor {
    /**
     * Invokes the protected getInputStreamByName method to read files from the pack root.
     */
    @Invoker("getInputStreamByName")
    InputStream invokeGetInputStreamByName(String name) throws IOException;

    /**
     * Invokes the protected hasResourceName method to check files at the pack root.
     */
    @Invoker("hasResourceName")
    boolean invokeHasResourceName(String name);
}
