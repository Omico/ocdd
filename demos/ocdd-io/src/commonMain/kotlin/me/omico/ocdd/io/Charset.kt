package me.omico.ocdd.io

import kotlin.jvm.JvmInline

@JvmInline
public value class Charset internal constructor(
    public val name: String,
) {
    public override fun toString(): String = name
}
