# Library Unpacker

Java application able to unpack Forge's .pack.xz files without hitting a "garbage after end of pack archive" error.

Based on [MinecraftForge/Installer](https://github.com/MinecraftForge/Installer)'s implementation.

    Usage: java -cp library-unpacker.jar nice.work.forge.LibraryUnpacker [-options] <file1> [file2...]
    where options include:
        --checksums   write checksums to jar
