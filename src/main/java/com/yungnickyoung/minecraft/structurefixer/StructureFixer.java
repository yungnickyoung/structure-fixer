package com.yungnickyoung.minecraft.structurefixer;

import com.mojang.datafixers.DataFixer;
import net.fabricmc.api.ModInitializer;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.nio.file.Path;
import java.util.Objects;

public class StructureFixer implements ModInitializer {
    private static int numFilesUpdated = 0;
    private static int numFilesFailed = 0;

    public static final Logger LOGGER = LogManager.getLogger("StructureFixer");

    @Override
    public void onInitialize() {
        DataFixer fixer = Minecraft.getInstance().getFixerUpper();

        File file = Path.of(".", "structures").toFile();
        Path outPath = Path.of(".", "structures_new");
        File output = outPath.toFile();
        file.mkdirs();
        output.mkdirs();

        StructureFixer.LOGGER.info("Updating structures in {}", outPath);

        boolean doCompleteRegen = true;
        updateAllInDirectory(file, "", outPath, fixer, doCompleteRegen);

        StructureFixer.LOGGER.info("{} files successfully updated, {} errors encountered.", numFilesUpdated, numFilesFailed);

    }

    private static void updateAllInDirectory(File directory, String path, Path output, DataFixer fixer, boolean doCompleteRegen) {
        for (File file : Objects.requireNonNull(directory.listFiles())) {
            String name = file.getName();

            // Recurse directories
            if (file.isDirectory()) {
                updateAllInDirectory(file, path + name + "/", output, fixer, doCompleteRegen);
                continue;
            }

            Path outDir = output.resolve(path);
            updateFile(file, outDir, fixer, doCompleteRegen);
        }
    }

    private static void updateFile(File file, Path outDir, DataFixer fixer, boolean doCompleteRegen) {
        String absolutePath = file.getAbsolutePath();
        String name = file.getName();

        StructureFixer.LOGGER.debug("Trying to update {}", absolutePath);

        if (file.isDirectory()) {
            numFilesFailed++;
            StructureFixer.LOGGER.error("Skipping {} because it is a directory", absolutePath);
            return;
        }

        try {
            CompoundTag currTag = NbtIo.readCompressed(file.toPath(), NbtAccounter.unlimitedHeap());
            int currVersion = NbtUtils.getDataVersion(currTag, 500);
            int newVersion = SharedConstants.getCurrentVersion().getDataVersion().getVersion();
            CompoundTag newTag;

            if (currVersion < newVersion) { // Only update if the file is outdated
                if (doCompleteRegen) {
                    StructureTemplate structureTemplate = new StructureTemplate();
                    structureTemplate.load(BuiltInRegistries.BLOCK.asLookup(), DataFixTypes.STRUCTURE.updateToCurrentVersion(fixer, currTag, currVersion));
                    newTag = structureTemplate.save(new CompoundTag());
                } else {
                    newTag = DataFixTypes.STRUCTURE.updateToCurrentVersion(fixer, currTag, currVersion);
                    newTag.putInt("DataVersion", newVersion);
                }

                // Write the new NBT to file
                outDir.toFile().mkdirs();
                NbtIo.writeCompressed(newTag, outDir.resolve(name));

                numFilesUpdated++;
                StructureFixer.LOGGER.debug("Updated {} from version {} to {}", absolutePath, currVersion, newTag.getInt("DataVersion"));
            }
        } catch (Exception e) {
            numFilesFailed++;
            StructureFixer.LOGGER.error("Could not update {}: {}", absolutePath, e.getMessage());
        }
    }
}
