package com.nao20010128nao.ClassNullifier;
import java.io.*;
import java.util.regex.*;
import joptsimple.*;
import java.util.zip.*;
import javassist.*;
import java.util.*;

//TODO: Rewrite in Groovy like Phosphorus
public class Main {
	public static void main(String... args)throws Throwable {
		OptionParser parser=new OptionParser();
		parser.accepts("input").withRequiredArg();
		parser.accepts("output").withOptionalArg();
		OptionSet result=parser.parse(args);
		File input,output;
		if (!result.has("input")) {
			System.exit(1);
			return;
		} else {
			input = new File(result.valueOf("input").toString());
		}
		if (!result.has("output")) {
			//TODO: Rename file : ..._nullified.jar
			output = new File(input.getParentFile(), last(input.getAbsolutePath().split(Pattern.quote(File.separator)))/*.last()*/+ "_nullified");
		} else {
			output = new File(result.valueOf("output").toString()).getAbsoluteFile();
		}

		ClassPool cp=new ClassPool(false);
		cp.appendClassPath(input.getAbsolutePath());
		Set<String> files=new HashSet<>();
		try(ZipInputStream zis=new ZipInputStream(new FileInputStream(input))){
			ZipEntry ze=null;
			while ((ze = zis.getNextEntry()) != null) {
				files.add(ze.getName());
			}
		}
		try(ZipOutputStream zos=new ZipOutputStream(new FileOutputStream(output))){
			for (String name:files) {
				try {
					if (!name.endsWith(".class")) {
						System.out.println("Skipped: " + name);
						continue;
					}
					String classNameGoes=name.replace(".class", "").replace("/", ".");
					System.out.println("Modifing: " + classNameGoes);
					CtClass clazz=cp.get(classNameGoes);
					if (clazz.isFrozen()) {
						System.out.println("Frozen: " + classNameGoes);
						clazz.defrost();
					}
					clazz.stopPruning(true);
					for (CtMethod method:clazz.getMethods()) {
						System.out.println(method.getName());
						if ((method.getModifiers() & Modifier.ABSTRACT) != 0) {
							continue;
						}
						CtClass ret=method.getReturnType();
						String bodyShouldBe="return null;";
						if (ret == CtClass.voidType) {
							bodyShouldBe = ";";
						} else if (ret.isPrimitive()) {
							bodyShouldBe = "return 0;";
						}
						method.setBody(bodyShouldBe);
					}
					for(CtConstructor cnst:clazz.getConstructors()){
						System.out.println("<init>");
						cnst.setBody(";");
					}
					if(clazz.getClassInitializer()!=null)
						clazz.getClassInitializer().setBody(";");
					ZipEntry newZe=new ZipEntry(name);
					zos.putNextEntry(newZe);
					zos.write(clazz.toBytecode());
					clazz.defrost();
				} catch (Throwable e) {
					e.printStackTrace();
				}
			}
		}

	}
	static String last(String... a) {
		return a[a.length - 1];
	}
}
