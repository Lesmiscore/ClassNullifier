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

ClassPool cp=new ClassPool(result.has("with-classpath"))
cp.appendClassPath(input.absolutePath)
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
				if (!name.endsWith(".class")) {
					println "Skipped: $name"
					return
				}
				String classNameGoes=name.substring(0,name.length()-6).replace("/", ".")
				println "Modifing: $classNameGoes"
				CtClass clazz=cp.get(classNameGoes)
				if (clazz.frozen) {
					println "Frozen: $classNameGoes"
					clazz.defrost()
				}
				clazz.stopPruning(true)
				clazz.methods.each{method->
					println "$method.name${overloads(method.parameterTypes)}"
					if ((method.modifiers & Modifier.ABSTRACT) != 0) {
						return
					}
					CtClass ret=method.returnType
					String bodyShouldBe="return null;"
					if (ret == CtClass.voidType) {
						bodyShouldBe = ";"
					} else if (ret.primitive) {
						bodyShouldBe = "return 0;"
					}
					method.body=bodyShouldBe
				}

				clazz.constructors.each{cnst->
					try{
						println "<init>${overloads(cnst.parameterTypes)}"
						cnst.body=";"
					} catch (Throwable e) {
						e.printStackTrace()
					}
				}
				if(clazz.classInitializer!=null)
					clazz.classInitializer.body=";"
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