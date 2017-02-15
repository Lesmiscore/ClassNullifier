# ClassNullifier
Makes Java methods blank in classes in a JAR/ZIP file.    
    
If there's a class file compiled from Java file like this,

```java
package com.nao20010128nao;
public class TestClass{
    public static void main(String[] args){
        System.out.println(test());
    }
    public static String test(){
        return "This is a test.";
    }
}
```
The Java file decompiled from result class file will look like:

```java
package com.nao20010128nao;
public class TestClass{
    public static void main(String[] args){
    }
    public static String test(){
        return null;
    }
}
```

For all the non-constructor methods, the code will be replaced to:

| Return Type                         | Code                          |
|:-----------------------------------:|:------------------------------|
| `void`                              | `;`(only one blank statement) |
| primitive types (except for arrays) | `return 0;`                   |
| non-primitive and array types       | `return null;`                |

For all the constructors and class initializers, the code will be replaced to `;`.    
Arguments are kept as is.    
Fields are unchangeable because javassist doesn't support to change them.    
Non-class file (e.g. `META-INF/MANIFEST.MF`) are ignored and it will not be included in the output.


# Usages
In command line:
- `--input=(filename)` Input file name (full path) of the JAR/ZIP file. (required)
- `--output=(dirname)` Output directory to save processed classes (default is to append `_nullified` on the `input`'s filename)

