package com.nao20010128nao.ClassNullifier

import java.io.*
import java.util.regex.*
import joptsimple.*
import java.util.zip.*
import javassist.*
import java.util.*

OptionParser parser=new OptionParser()
parser.accepts("input").withRequiredArg()
parser.accepts("output").withOptionalArg()
parser.accepts("with-classpath")
parser.accepts("remove-private")
def result=parser.parse(args)
File input,output
if (!result.has("input")) {
	println "input argument is required."
	System.exit 1
	return
} else {
	input = new File(result.valueOf("input").toString())
	println "IN: $input"
}
if (!result.has("output")) {
	def renameFilename={String s->
		def filename=s.split(Pattern.quote(File.separator)).last()
		def nonExtFn=filename.substring(0,filename.lastIndexOf("."))
		def ext=filename.substring(filename.lastIndexOf(".")+1)
		nonExtFn+="_nullified"
		return nonExtFn+"."+ext
	}
	output = new File(input.parentFile, renameFilename(input.absolutePath))
} else {
	output = new File(result.valueOf("output").toString()).absoluteFile
}
println "OUT: $output"

// make ClassPool object
ClassPool cp=new ClassPool(result.has("with-classpath"))
cp.appendClassPath(input.absolutePath)

// list up files first to prevent opening file
//   with 2 or more descriptors at same time
Set<String> files=[]
input.withDataInputStream {dis->
	ZipInputStream zis=null
	try{
		zis=new ZipInputStream(dis)
		ZipEntry ze=null
		while ((ze = zis.nextEntry) != null) {
			files+=ze.name
		}
	}finally{
		if(zis!=null)
			zis.close()
	}
}

// do something like this: main(String[])
def overloads={CtClass[] classes->
	if(classes.length==0)return "()"
	def builder=new StringBuilder()
	builder<<"("
	classes.each {clazz->
		builder<<clazz.name<<", "
	}
	builder.length=builder.length()-2
	builder<<")"
	return builder.toString()
}
output.withDataOutputStream {dos->
	ZipOutputStream zos=null
	try{
		zos=new ZipOutputStream(dos)
		files.each{name->
			try {
				// Non-class file will be skipped
				if (!name.endsWith(".class")) {
					println "Skipped: $name"
					return
				}
				// Example:
				// com/nao20010128nao/ClassNullifier/Main.class
				// VVV (remove .class in the tail)
				// com/nao20010128nao/ClassNullifier/Main
				// VVV (replace "/" to ".")
				// com.nao20010128nao.ClassNullifier.Main
				String classNameGoes=name.substring(0,name.length()-6).replace("/", ".")
				println "Modifing: $classNameGoes"
				CtClass clazz=cp.get(classNameGoes)
				if (clazz.frozen) {
					println "Frozen: $classNameGoes"
					clazz.defrost()
				}
				clazz.stopPruning(true)
				clazz.declaredMethods.each{method->
					try{
						if(result.has("remove-private")){
							if((method.modifiers&Modifier.PRIVATE)!=0){
								println "Remove: $method.name${overloads(method.parameterTypes)}"
								clazz.removeMethod(method)
								return
							}
						}
						if ((method.modifiers & Modifier.ABSTRACT) != 0) {
							// skip: abstract method
							println "Skip: $method.name${overloads(method.parameterTypes)}"
							return
						}
						println "$method.name${overloads(method.parameterTypes)}"
						// make it non-native: to prevent UnsatisfiedLinkError
						method.modifiers&=~Modifier.NATIVE

						CtClass ret = method.returnType
						// default is to return null; for non-primitive return types
						String bodyShouldBe = "return null;"
						if (ret == CtClass.voidType) {
							// if return type is void, let it do nothing,
							//   because there's no need to return value
							bodyShouldBe = ";"
						} else if (ret.primitive) {
							// if return type is one of primitive type, let it return 0
							bodyShouldBe = "return 0;"
						}
						method.body = bodyShouldBe
					} catch (Throwable e) {
						e.printStackTrace()
					}
				}

				clazz.declaredConstructors.each{cnst->
					if(result.has("remove-private")){
						if((cnst.modifiers&Modifier.PRIVATE)!=0){
							println "Remove: <init>${overloads(cnst.parameterTypes)}"
							clazz.removeConstructor(cnst)
							return
						}
					}
					// any constructors cannot be abstract or native,
					//   their code must be implemented by Java code
					try {
						// let it do nothing
						println "<init>${overloads(cnst.parameterTypes)}"
						cnst.body = ";"
					} catch (Throwable e) {
						e.printStackTrace()
					}
				}

				if(result.has("remove-private")){
					// remove private fields
					clazz.declaredFields.each {field->
						if((field.modifiers&Modifier.PRIVATE)!=0){
							clazz.removeField(field)
						}
					}
				}

				// <cinit>
				if(clazz.classInitializer!=null)
					clazz.classInitializer.body=";"

				// put a class into JAR/ZIP
				ZipEntry newZe=new ZipEntry(name)
				zos.putNextEntry(newZe)
				zos.write(clazz.toBytecode())
				clazz.defrost()
			} catch (Throwable e) {
				e.printStackTrace()
			}
		}
	}finally{
		if(zos!=null)
			zos.close()
	}
}