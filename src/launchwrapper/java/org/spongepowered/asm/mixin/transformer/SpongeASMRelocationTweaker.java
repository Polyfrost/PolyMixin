package org.spongepowered.asm.mixin.transformer;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

public class SpongeASMRelocationTweaker implements IClassTransformer {
    private static final String SPONGE_PACKAGE = "org/spongepowered/asm/lib/";

    private static final byte[] SPONGE_PACKAGE_BYTES = SPONGE_PACKAGE.getBytes(StandardCharsets.UTF_8);

    private static final Remapper remapper = new Remapper() {
        @Override
        public String map(String typeName) {
            if (typeName.startsWith(SPONGE_PACKAGE)) {
                return "org/objectweb/asm/" + typeName.substring(SPONGE_PACKAGE.length());
            } else {
                return typeName;
            }
        }
    };

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || bytesIndexOf(bytes, SPONGE_PACKAGE_BYTES) == -1) {
            return bytes;
        }


        ClassReader classReader = new ClassReader(bytes);
        ClassWriter classWriter = new ClassWriter(0);
        try {
            classReader.accept(new ClassRemapper(new DuplicateClassWriter(classWriter), remapper), 0);
            return classWriter.toByteArray();
        } catch (DuplicateMethodException ignored) {

        }
        return bytes;
    }

    private static class DuplicateClassWriter extends ClassVisitor {
        private final Set<String> detectedMethods = new HashSet<>();

        private DuplicateClassWriter(ClassWriter writer) {
            super(Opcodes.ASM5);
            this.cv = writer;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            String identifier = name + desc;
            if (detectedMethods.contains(identifier)) {
                throw new DuplicateMethodException(identifier);
            }
            detectedMethods.add(identifier);

            return super.visitMethod(access, name, desc, signature, exceptions);
        }
    }

    private static class DuplicateMethodException extends RuntimeException {
        private DuplicateMethodException(String message) {
            super(message);
        }
    }

    // taken from guava
    private static int bytesIndexOf(byte[] array, byte[] target) {
        if (target.length == 0) {
            return 0;
        }

        outer:
        for (int i = 0; i < array.length - target.length + 1; i++) {
            for (int j = 0; j < target.length; j++) {
                if (array[i + j] != target[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
