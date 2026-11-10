package top.madkarma.conduit.mixin;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.SavedDataStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The saved data that the game has loaded, which is newer than its file between saves. */
@Mixin(SavedDataStorage.class)
public interface SavedDataStorageAccessor {
    @Accessor
    Map<SavedDataType<?>, Optional<SavedData>> getCache();

    @Accessor
    Path getDataFolder();
}
