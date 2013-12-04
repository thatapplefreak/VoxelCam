package com.thatapplefreak.voxelcam;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import net.minecraft.launchwrapper.IClassTransformer;

public class VoxelCamTransformer implements IClassTransformer {

	@Override
	public byte[] transform(String name, String transformedName, byte[] basicClass) {
		if ("net.minecraft.client.Minecraft".equals(name) || "azd".equals(name)) {
			ClassReader classReader = new ClassReader(basicClass);
			ClassNode classNode = new ClassNode();
			classReader.accept(classNode, ClassReader.EXPAND_FRAMES);

			for (MethodNode method : classNode.methods) {
				if (("screenshotListener".equals(method.name) || "func_71365_K".equals(method.name) || "ae".equals(method.name)) && "()V".equals(method.desc)) {
					this.transformScreenshotListener(method);
				}
			}

			ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
			classNode.accept(writer);
			return writer.toByteArray();
		}

		return basicClass;
	}

	/**
	 * @param method
	 */
	public void transformScreenshotListener(MethodNode method) {
		InsnList code = new InsnList();
		code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/thatapplefreak/voxelcam/LiteModVoxelCam", "screenshotListener", "()V"));
		code.add(new InsnNode(Opcodes.RETURN));
		method.instructions.insert(code);
	}

}
