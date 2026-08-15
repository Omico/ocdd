package me.omico.ocdd.io

@Throws(IOException::class)
public expect fun Path.deleteExisting()

@Throws(IOException::class)
public expect fun Path.deleteIfExists(): Boolean

@Throws(IOException::class)
public expect fun Path.deleteRecursively()
