// Generated + copied data and assets (TFC JSON, CE pack, BetterHud pack, models).
// Produced by :tools:datagen, never edited by hand. Shipped as jar resources under /content.
plugins { `java-library` }
// The jar packs content/generated directly. A resources source set would first copy the whole tree (~110 MB) into
// build/resources/main in every worktree. content/generated may be a symlink to a read-only shared store entry
// (tools/worktree/share-build.sh); this module only reads it.
sourceSets.main { resources.setSrcDirs(emptyList<String>()) }
tasks.jar { from("generated") }
