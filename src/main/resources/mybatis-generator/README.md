# Mybatis代码生成器使用指南

## 如何从数据库生成Java类?

编辑generatorConfig.xml文件,在其中加入对应的数据库->Java类映射配置,如:

```xml
		<!-- 数据库->实体映射配置,table标签可以配置多个 -->
		<table tableName="activity" domainObjectName="Activity"
			enableCountByExample="false" enableUpdateByExample="false"
			enableDeleteByExample="false" enableSelectByExample="false"
			selectByExampleQueryId="false">
			<columnOverride column="deleted" javaType="boolean" />
		</table>
```

## 执行maven命令生成代码

```shell
mvn mybatis-generator:generate
```

## 注意事项

生成代码时请去掉不需要生成实体的<table>配置,避免覆盖已有代码

