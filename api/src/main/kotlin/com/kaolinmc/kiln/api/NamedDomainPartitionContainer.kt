package com.kaolinmc.kiln.api

import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer

public abstract class NamedDomainPartitionContainer(
    delegate: NamedDomainObjectContainer<PartitionHandler<*>>,
    public val extension: KaolinExtension
) : NamedDomainObjectContainer<PartitionHandler<*>> by delegate {
    public abstract fun <T : PartitionHandler<*>> doAdd(action: Action<T>, getHandler: ((() -> Unit) -> Unit) -> T): T

    public abstract fun tweaker(action: Action<TweakerPartitionHandler>)

    public abstract fun gradle(action: Action<GradlePartitionHandler>)

    public fun has(name: String) : Boolean {
        return findByName(name) != null
    }
//    abstract fun version(name: String, action: Action<MinecraftPartitionHandler>)
}