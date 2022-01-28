package org.lflang.generator.cpp

import org.lflang.generator.PrependOperator

class CppRos2PackageGenerator(private val generator: CppGenerator) {
    private val fileConfig = generator.cppFileConfig

    fun generatePackageXml(): String {
        return with(PrependOperator) {
            """
                |<?xml version="1.0"?>
                |<?xml-model href="http://download.ros.org/schema/package_format3.xsd" schematypens="http://www.w3.org/2001/XMLSchema"?>
                |<package format="3">
                |  <name>${fileConfig.name}</name>
                |  <version>0.0.0</version>
                |  <description>Autogenerated from ${fileConfig.srcFile}</description>
                |  <maintainer email="todo@todo.com">Todo</maintainer>
                |  <license>Todo</license>
                |
                |  <buildtool_depend>ament_cmake</buildtool_depend>
                |  
                |  <depend>rclcpp</depend>
                |  <depend>std_msgs</depend>
                |  <depend>reactor-cpp</depend>
                |
                |  <test_depend>ament_lint_auto</test_depend>
                |  <test_depend>ament_lint_common</test_depend>
                |
                |  <export>
                |    <build_type>ament_cmake</build_type>
                |  </export>
                |</package>
            """.trimMargin()
        }
    }
}