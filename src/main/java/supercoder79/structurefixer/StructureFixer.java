package supercoder79.structurefixer;

import com.mojang.datafixers.DataFixer;
import net.fabricmc.api.ModInitializer;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.DataFixTypes;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.nio.file.Path;

public class StructureFixer implements ModInitializer {
    public static final Logger LOGGER = LogManager.getLogger("StructureFixer");

    private static int numFilesUpdated = 0;
    private static int numFilesFailed = 0;

    @Override
    public void onInitialize() {
        DataFixer fixer = Minecraft.getInstance().getFixerUpper();

        File file = Path.of(".", "structures").toFile();
        Path outPath = Path.of(".", "structures_new");
        File output = outPath.toFile();
        file.mkdirs();
        output.mkdirs();

        LOGGER.info("Updating structures in {}", outPath);
        updateAllInDirectory(file, "", outPath, fixer);
        LOGGER.info("{} files successfully updated, {} errors encountered.", numFilesUpdated, numFilesFailed);
    }

    private static void updateAllInDirectory(File directory, String path, Path output, DataFixer fixer) {
        for (File file : directory.listFiles()) {
            String name = file.getName();

            // Recurse directories
            if (file.isDirectory()) {
                updateAllInDirectory(file, path + name + "/", output, fixer);
                continue;
            }

            Path outDir = output.resolve(path);
            updateFile(file, outDir, fixer);
        }
    }

    private static void updateFile(File file, Path outDir, DataFixer fixer) {
        String absolutePath = file.getAbsolutePath();
        String name = file.getName();

        LOGGER.debug("Trying to update {}", absolutePath);

        if (file.isDirectory()) {
            numFilesFailed++;
            LOGGER.error("Skipping {} because it is a directory", absolutePath);
            return;
        }

        try {
            CompoundTag currentTag = NbtIo.readCompressed(file.toPath(), NbtAccounter.unlimitedHeap());

            if (!currentTag.contains("DataVersion", Tag.TAG_ANY_NUMERIC)) {
                currentTag.putInt("DataVersion", 500);
            }

            int currentDataVersion = currentTag.getInt("DataVersion");
            int newDataVersion = SharedConstants.getCurrentVersion().getDataVersion().getVersion();

            if (currentDataVersion < newDataVersion) {
                currentTag = DataFixTypes.STRUCTURE.update(fixer, currentTag, currentDataVersion, newDataVersion);
                currentTag.putInt("DataVersion", newDataVersion);

                // Write the new NBT to file
                outDir.toFile().mkdirs();
                NbtIo.writeCompressed(currentTag, outDir.resolve(name));

                numFilesUpdated++;
                LOGGER.debug("Updated {} from version {} to {}", absolutePath, currentDataVersion, currentTag.getInt("DataVersion"));
            }
        } catch (Exception e) {
            numFilesFailed++;
            LOGGER.error("Could not update {}: {}", absolutePath, e.getMessage());
        }
    }
}
