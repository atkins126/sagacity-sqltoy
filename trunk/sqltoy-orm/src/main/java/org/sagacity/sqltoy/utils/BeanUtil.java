package org.sagacity.sqltoy.utils;

import static java.lang.System.err;

import java.io.BufferedReader;
import java.io.Serializable;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Array;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.sagacity.sqltoy.callback.ReflectPropertyHandler;
import org.sagacity.sqltoy.config.annotation.SqlToyEntity;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.plugins.TypeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy4.0
 * @description 类处理通用工具,提供反射处理
 * @author zhongxuchen
 * @version v1.0,Date:2008-11-10
 * @modify data:2019-09-05 优化匹配方式，修复setIsXXX的错误
 * @modify data:2020-06-23 优化convertType(Object, String) 方法
 * @modify data:2020-07-08 修复convertType(Object, String) 转Long类型时精度丢失问题
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class BeanUtil {
	/**
	 * 定义日志
	 */
	protected final static Logger logger = LoggerFactory.getLogger(BeanUtil.class);

	/**
	 * 保存set方法
	 */
	private static ConcurrentHashMap<String, Method> setMethods = new ConcurrentHashMap<String, Method>();

	/**
	 * 保存get方法
	 */
	private static ConcurrentHashMap<String, Method> getMethods = new ConcurrentHashMap<String, Method>();

	// 静态方法避免实例化和继承
	private BeanUtil() {

	}

	/**
	 * <p>
	 * <li>update 2019-09-05 优化匹配方式，修复setIsXXX的错误</li>
	 * <li>update 2020-04-09 支持setXXX()并返回对象本身,适配链式操作</li>
	 * </p>
	 * 
	 * @todo 获取指定名称的方法集
	 * @param voClass
	 * @param props
	 * @return
	 */
	public static Method[] matchSetMethods(Class voClass, String... props) {
		int indexSize = props.length;
		Method[] result = new Method[indexSize];
		Method[] methods = voClass.getMethods();
		// 先过滤出全是set且只有一个参数的方法
		List<Method> realMeth = new ArrayList<Method>();
		for (Method mt : methods) {
			// 剔除void 判断条件
			// if (mt.getParameterTypes().length == 1 &&
			// void.class.equals(mt.getReturnType())) {
			if (mt.getParameterTypes().length == 1) {
				if (mt.getName().startsWith("set")) {
					realMeth.add(mt);
				}
			}
		}
		if (realMeth.isEmpty()) {
			return result;
		}
		Method method;
		String prop;
		boolean matched = false;
		String name;
		Class type;
		for (int i = 0; i < indexSize; i++) {
			if (props[i] != null) {
				prop = "set".concat(props[i].toLowerCase());
				matched = false;
				for (int j = 0; j < realMeth.size(); j++) {
					method = realMeth.get(j);
					// 放弃兼容属性名称有下划线模式
					// name=method.getName().replaceAll("\\_", "").toLowerCase();
					name = method.getName().toLowerCase();
					// setXXX完全匹配
					if (prop.equals(name)) {
						matched = true;
					} else {
						// boolean 类型参数
						type = method.getParameterTypes()[0];
						if ((type.equals(Boolean.class) || type.equals(boolean.class)) && prop.startsWith("setis")
								&& prop.replaceFirst("setis", "set").equals(name)) {
							matched = true;
						}
					}
					if (matched) {
						result[i] = method;
						result[i].setAccessible(true);
						realMeth.remove(j);
						break;
					}
				}
				if (realMeth.isEmpty()) {
					break;
				}
			}
		}
		return result;
	}

	/**
	 * @todo 获取指定名称的方法集,不区分大小写
	 * @param voClass
	 * @param props
	 * @return
	 */
	public static Method[] matchGetMethods(Class voClass, String... props) {
		Method[] methods = voClass.getMethods();
		List<Method> realMeth = new ArrayList<Method>();
		String name;
		// 过滤get 和is 开头的方法
		for (Method mt : methods) {
			if (!void.class.equals(mt.getReturnType()) && mt.getParameterTypes().length == 0) {
				name = mt.getName().toLowerCase();
				if (name.startsWith("get") || name.startsWith("is")) {
					realMeth.add(mt);
				}
			}
		}
		int indexSize = props.length;
		Method[] result = new Method[indexSize];
		if (realMeth.isEmpty()) {
			return result;
		}
		String prop;
		Method method;
		boolean matched = false;
		Class type;
		for (int i = 0; i < indexSize; i++) {
			if (props[i] != null) {
				prop = props[i].toLowerCase();
				matched = false;
				for (int j = 0; j < realMeth.size(); j++) {
					method = realMeth.get(j);
					// 放弃兼容属性名称有下划线模式
					// name=method.getName().replaceAll("\\_", "").toLowerCase();
					name = method.getName().toLowerCase();
					// get完全匹配
					if (name.equals("get".concat(prop))) {
						matched = true;
					} else if (name.startsWith("is")) {
						// boolean型 is开头的方法
						type = method.getReturnType();
						if ((type.equals(Boolean.class) || type.equals(boolean.class))
								&& (name.equals(prop) || name.equals("is".concat(prop)))) {
							matched = true;
						}
					}
					if (matched) {
						result[i] = method;
						result[i].setAccessible(true);
						realMeth.remove(j);
						break;
					}
				}
				if (realMeth.isEmpty()) {
					break;
				}
			}
		}
		return result;
	}

	/**
	 * @todo 获取指定名称的方法集,不区分大小写
	 * @param voClass
	 * @param properties
	 * @return
	 */
	public static Integer[] matchMethodsType(Class voClass, String... properties) {
		if (properties == null || properties.length == 0) {
			return null;
		}
		int indexSize = properties.length;
		Method[] methods = voClass.getMethods();
		Integer[] fieldsType = new Integer[indexSize];
		String methodName;
		String typeName;
		int methodCnt = methods.length;
		String property;
		Method method;
		for (int i = 0; i < indexSize; i++) {
			fieldsType[i] = java.sql.Types.NULL;
			property = properties[i].toLowerCase();
			for (int j = 0; j < methodCnt; j++) {
				method = methods[j];
				methodName = method.getName().toLowerCase();
				// update 2012-10-25 from equals to ignoreCase
				if (!void.class.equals(method.getReturnType()) && method.getParameterTypes().length == 0
						&& (methodName.equals("get".concat(property)) || methodName.equals("is".concat(property))
								|| (methodName.startsWith("is") && methodName.equals(property)))) {
					typeName = method.getReturnType().getSimpleName().toLowerCase();
					if (typeName.equals("string")) {
						fieldsType[i] = java.sql.Types.VARCHAR;
					} else if (typeName.equals("integer")) {
						fieldsType[i] = java.sql.Types.INTEGER;
					} else if (typeName.equals("bigdecimal")) {
						fieldsType[i] = java.sql.Types.DECIMAL;
					} else if (typeName.equals("date")) {
						fieldsType[i] = java.sql.Types.DATE;
					} else if (typeName.equals("timestamp")) {
						fieldsType[i] = java.sql.Types.TIMESTAMP;
					} else if (typeName.equals("int")) {
						fieldsType[i] = java.sql.Types.INTEGER;
					} else if (typeName.equals("long")) {
						fieldsType[i] = java.sql.Types.NUMERIC;
					} else if (typeName.equals("double")) {
						fieldsType[i] = java.sql.Types.DOUBLE;
					} else if (typeName.equals("clob")) {
						fieldsType[i] = java.sql.Types.CLOB;
					} else if (typeName.equals("biginteger")) {
						fieldsType[i] = java.sql.Types.BIGINT;
					} else if (typeName.equals("blob")) {
						fieldsType[i] = java.sql.Types.BLOB;
					} else if (typeName.equals("[b")) {
						fieldsType[i] = java.sql.Types.BINARY;
					} else if (typeName.equals("boolean")) {
						fieldsType[i] = java.sql.Types.BOOLEAN;
					} else if (typeName.equals("char")) {
						fieldsType[i] = java.sql.Types.CHAR;
					} else if (typeName.equals("number")) {
						fieldsType[i] = java.sql.Types.NUMERIC;
					} else if (typeName.equals("short")) {
						fieldsType[i] = java.sql.Types.NUMERIC;
					} else if (typeName.equals("float")) {
						fieldsType[i] = java.sql.Types.FLOAT;
					} else if (typeName.equals("datetime")) {
						fieldsType[i] = java.sql.Types.DATE;
					} else if (typeName.equals("time")) {
						fieldsType[i] = java.sql.Types.TIME;
					} else if (typeName.equals("byte")) {
						fieldsType[i] = java.sql.Types.TINYINT;
					} else {
						fieldsType[i] = java.sql.Types.NULL;
					}
					break;
				}
			}
		}
		return fieldsType;
	}

	/**
	 * @todo 类的方法调用
	 * @param bean
	 * @param methodName
	 * @param args
	 * @return
	 * @throws Exception
	 */
	public static Object invokeMethod(Object bean, String methodName, Object[] args) throws Exception {
		try {
			Method method = getMethod(bean.getClass(), methodName, args == null ? 0 : args.length);
			if (method == null) {
				return null;
			}
			return method.invoke(bean, args);
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		}
	}

	/**
	 * @todo <b>对象比较</b>
	 * @param target
	 * @param compared
	 * @return
	 */
	public static boolean equals(Object target, Object compared) {
		if (null == target) {
			return target == compared;
		}
		return target.equals(compared);
	}

	/**
	 * @todo 用于不同类型数据之间进行比较，判断是否相等,当类型不一致时统一用String类型比较
	 * @param target
	 * @param compared
	 * @param ignoreCase
	 * @return
	 */
	public static boolean equalsIgnoreType(Object target, Object compared, boolean ignoreCase) {
		if (target == null || compared == null) {
			return target == compared;
		}
		if (target.getClass().equals(compared.getClass()) && !(target instanceof CharSequence)) {
			return target.equals(compared);
		}
		if (ignoreCase) {
			return target.toString().equalsIgnoreCase(compared.toString());
		}
		return target.toString().equals(compared.toString());
	}

	/**
	 * @TODO 比较两个对象的大小
	 * @param target
	 * @param compared
	 * @return
	 */
	public static int compare(Object target, Object compared) {
		if (null == target && null == compared) {
			return 0;
		}
		if (null == target) {
			return -1;
		}
		if (null == compared) {
			return 1;
		}

		// 直接相等
		if (target.equals(compared))
			return 0;
		// 日期类型
		if ((target instanceof Date || target instanceof LocalDate || target instanceof LocalTime
				|| target instanceof LocalDateTime)
				|| (compared instanceof Date || compared instanceof LocalDate || compared instanceof LocalTime
						|| compared instanceof LocalDateTime)) {
			return DateUtil.convertDateObject(target).compareTo(DateUtil.convertDateObject(compared));
		} // 数字
		else if ((target instanceof Number) || (compared instanceof Number)) {
			return new BigDecimal(target.toString()).compareTo(new BigDecimal(compared.toString()));
		} else {
			return target.toString().compareTo(compared.toString());
		}
	}

	public static Object convertType(Object value, String typeName) throws Exception {
		return convertType(null, value, typeName, null);
	}

	/**
	 * @todo 类型转换
	 * @param typeHandler
	 * @param value
	 * @param typeOriginName
	 * @param genericType    泛型类型
	 * @return
	 * @throws Exception
	 */
	public static Object convertType(TypeHandler typeHandler, Object value, String typeOriginName, Class genericType)
			throws Exception {
		Object paramValue = value;
		// 转换为小写
		String typeName = typeOriginName.toLowerCase();
		// 非数组类型,但传递的参数值是数组类型,提取第一个参数
		if (!typeName.contains("[]") && paramValue != null && paramValue.getClass().isArray()) {
			paramValue = CollectionUtil.convertArray(paramValue)[0];
		}
		if (paramValue == null) {
			if (typeName.equals("int") || typeName.equals("long") || typeName.equals("double")
					|| typeName.equals("float") || typeName.equals("short")) {
				return 0;
			}
			if (typeName.equals("boolean") || typeName.equals("java.lang.boolean")) {
				return false;
			}
			return null;
		}
		// value值的类型跟目标类型一致，直接返回
		if (value.getClass().getTypeName().toLowerCase().equals(typeName)) {
			return value;
		}
		// 针对非常规类型转换，将jdbc获取的字段结果转为java对象属性对应的类型
		if (typeHandler != null) {
			Object result = typeHandler.toJavaType(typeOriginName, genericType, paramValue);
			if (result != null) {
				return result;
			}
		}
		String valueStr = paramValue.toString();
		// 字符串第一优先
		if (typeName.equals("string") || typeName.equals("java.lang.string")) {
			if (paramValue instanceof java.sql.Clob) {
				java.sql.Clob clob = (java.sql.Clob) paramValue;
				return clob.getSubString((long) 1, (int) clob.length());
			}
			if (paramValue instanceof java.util.Date) {
				return DateUtil.formatDate(paramValue, "yyyy-MM-dd HH:mm:ss");
			}
			return valueStr;
		}
		// 第二优先
		if (typeName.equals("java.math.bigdecimal") || typeName.equals("decimal") || typeName.equals("bigdecimal")) {
			return new BigDecimal(convertBoolean(valueStr));
		}
		// 第三优先
		if (typeName.equals("java.time.localdatetime")) {
			if (paramValue instanceof LocalDateTime) {
				return (LocalDateTime) paramValue;
			}
			return DateUtil.asLocalDateTime(DateUtil.convertDateObject(paramValue));
		}
		// 第四
		if (typeName.equals("java.time.localdate")) {
			if (paramValue instanceof LocalDate) {
				return (LocalDate) paramValue;
			}
			return DateUtil.asLocalDate(DateUtil.convertDateObject(paramValue));
		}
		// 第五
		if (typeName.equals("java.lang.integer") || typeName.equals("integer")) {
			return Integer.valueOf(convertBoolean(valueStr).split("\\.")[0]);
		}
		// 第六
		if (typeName.equals("java.sql.timestamp") || typeName.equals("timestamp")) {
			if (paramValue instanceof java.sql.Timestamp) {
				return (java.sql.Timestamp) paramValue;
			}
			if (paramValue instanceof java.util.Date) {
				return new Timestamp(((java.util.Date) paramValue).getTime());
			}
			if (paramValue.getClass().getTypeName().toLowerCase().equals("oracle.sql.timestamp")) {
				return oracleTimeStampConvert(paramValue);
			}
			return new Timestamp(DateUtil.parseString(valueStr).getTime());
		}
		if (typeName.equals("java.lang.double")) {
			return Double.valueOf(valueStr);
		}
		if (typeName.equals("java.util.date") || typeName.equals("date")) {
			if (paramValue instanceof java.util.Date) {
				return (java.util.Date) paramValue;
			}
			if (paramValue instanceof Number) {
				return new java.util.Date(((Number) paramValue).longValue());
			}
			if (paramValue.getClass().getTypeName().toLowerCase().equals("oracle.sql.timestamp")) {
				return new java.util.Date(oracleDateConvert(paramValue).getTime());
			}
			return DateUtil.parseString(valueStr);
		}
		if (typeName.equals("java.lang.long")) {
			// 考虑数据库中存在默认值为0.00 的问题，导致new Long() 报错
			return Long.valueOf(convertBoolean(valueStr).split("\\.")[0]);
		}
		if (typeName.equals("int")) {
			return Double.valueOf(convertBoolean(valueStr)).intValue();
		}
		// clob 类型比较特殊,对外转类型全部转为字符串
		if (typeName.equals("java.sql.clob") || typeName.equals("clob")) {
			// update 2020-6-23 增加兼容性判断
			if (paramValue instanceof String) {
				return valueStr;
			}
			java.sql.Clob clob = (java.sql.Clob) paramValue;
			BufferedReader in = new BufferedReader(clob.getCharacterStream());
			StringWriter out = new StringWriter();
			int c;
			while ((c = in.read()) != -1) {
				out.write(c);
			}
			return out.toString();
		}
		if (typeName.equals("java.time.localtime")) {
			if (paramValue instanceof LocalTime) {
				return (LocalTime) paramValue;
			}
			return DateUtil.asLocalTime(DateUtil.convertDateObject(paramValue));
		}
		// add 2020-4-9
		if (typeName.equals("java.math.biginteger") || typeName.equals("biginteger")) {
			return new BigInteger(convertBoolean(valueStr).split("\\.")[0]);
		}
		if (typeName.equals("long")) {
			return Double.valueOf(convertBoolean(valueStr)).longValue();
		}
		if (typeName.equals("double")) {
			return Double.valueOf(valueStr).doubleValue();
		}
		// update by 2020-4-13增加Byte类型的处理
		if (typeName.equals("java.lang.byte")) {
			return Byte.valueOf(valueStr);
		}
		if (typeName.equals("byte")) {
			return Byte.valueOf(valueStr).byteValue();
		}
		// byte数组
		if (typeName.equals("byte[]") || typeName.equals("[b")) {
			if (paramValue instanceof byte[]) {
				return (byte[]) paramValue;
			}
			// blob类型处理
			if (paramValue instanceof java.sql.Blob) {
				java.sql.Blob blob = (java.sql.Blob) paramValue;
				return blob.getBytes(1, (int) blob.length());
			}
			return valueStr.getBytes();
		}
		// 字符串转 boolean 型
		if (typeName.equals("java.lang.boolean") || typeName.equals("boolean")) {
			if (valueStr.toLowerCase().equals("true") || valueStr.equals("1")) {
				return Boolean.TRUE;
			}
			return Boolean.FALSE;
		}
		if (typeName.equals("java.lang.short")) {
			return Short.valueOf(Double.valueOf(convertBoolean(valueStr)).shortValue());
		}
		if (typeName.equals("short")) {
			return Double.valueOf(convertBoolean(valueStr)).shortValue();
		}
		if (typeName.equals("java.lang.float")) {
			return Float.valueOf(valueStr);
		}
		if (typeName.equals("float")) {
			return Float.valueOf(valueStr).floatValue();
		}
		if (typeName.equals("java.sql.date")) {
			if (paramValue instanceof java.sql.Date) {
				return (java.sql.Date) paramValue;
			}
			if (paramValue instanceof java.util.Date) {
				return new java.sql.Date(((java.util.Date) paramValue).getTime());
			}

			if (paramValue.getClass().getTypeName().toLowerCase().equals("oracle.sql.timestamp")) {
				return new java.sql.Date(oracleDateConvert(paramValue).getTime());
			}
			return new java.sql.Date(DateUtil.parseString(valueStr).getTime());
		}
		if (typeName.equals("char")) {
			return valueStr.charAt(0);
		}
		if (typeName.equals("java.sql.time") || typeName.equals("time")) {
			if (paramValue instanceof java.sql.Time) {
				return (java.sql.Time) paramValue;
			}
			if (paramValue instanceof java.util.Date) {
				return new java.sql.Time(((java.util.Date) paramValue).getTime());
			}

			if (paramValue.getClass().getTypeName().toLowerCase().equals("oracle.sql.timestamp")) {
				return new java.sql.Time(oracleDateConvert(paramValue).getTime());
			}
			return DateUtil.parseString(valueStr);
		}
		// 字符数组
		if (typeName.equals("char[]") || typeName.equals("[c")) {
			if (paramValue instanceof char[]) {
				return (char[]) paramValue;
			}
			if (paramValue instanceof java.sql.Clob) {
				java.sql.Clob clob = (java.sql.Clob) paramValue;
				BufferedReader in = new BufferedReader(clob.getCharacterStream());
				StringWriter out = new StringWriter();
				int c;
				while ((c = in.read()) != -1) {
					out.write(c);
				}
				return out.toString().toCharArray();
			}
			return valueStr.toCharArray();
		}
		// 数组类型
		if (typeName.contains("[") || typeName.contains("[]") && paramValue instanceof Array) {
			return convertArray(((Array) paramValue).getArray(), typeName);
		}

		return paramValue;
	}

	private static String convertBoolean(String var) {
		if (var.equals("true")) {
			return "1";
		}
		if (var.equals("false")) {
			return "0";
		}
		return var;
	}

	private static Timestamp oracleTimeStampConvert(Object obj) throws Exception {
		return ((oracle.sql.TIMESTAMP) obj).timestampValue();
	}

	private static Date oracleDateConvert(Object obj) throws Exception {
		return ((oracle.sql.TIMESTAMP) obj).dateValue();
	}

	/**
	 * @todo 利用java.lang.reflect并结合页面的property， 从对象中取出对应方法的值，组成一个List
	 * @param datas
	 * @param props
	 * @return
	 */
	public static List reflectBeansToList(List datas, String[] props) throws Exception {
		return reflectBeansToList(datas, props, null, false, 1);
	}

	public static List reflectBeanToList(Object data, String[] properties) throws Exception {
		return reflectBeanToList(data, properties, null);
	}

	public static List reflectBeanToList(Object data, String[] properties,
			ReflectPropertyHandler reflectPropertyHandler) throws Exception {
		List datas = new ArrayList();
		datas.add(data);
		List result = reflectBeansToList(datas, properties, reflectPropertyHandler, false, 0);
		if (null != result && !result.isEmpty()) {
			return (List) result.get(0);
		}
		return null;
	}

	public static List reflectBeansToList(List datas, String[] properties, boolean hasSequence, int startSequence)
			throws Exception {
		return reflectBeansToList(datas, properties, null, hasSequence, startSequence);
	}

	/**
	 * @todo 利用java.lang.reflect并结合页面的property， 从对象中取出对应方法的值，组成一个List
	 * @param datas
	 * @param properties
	 * @param reflectPropertyHandler
	 * @param hasSequence
	 * @param startSequence
	 * @return
	 * @throws Exception
	 */
	public static List reflectBeansToList(List datas, String[] properties,
			ReflectPropertyHandler reflectPropertyHandler, boolean hasSequence, int startSequence) throws Exception {
		if (null == datas || datas.isEmpty() || null == properties || properties.length < 1) {
			return null;
		}
		// 数据的长度
		int maxLength = Integer.toString(datas.size()).length();
		List resultList = new ArrayList();
		try {
			int methodLength = properties.length;
			Method[] realMethods = null;
			boolean inited = false;
			Object rowObject = null;
			Object[] params = new Object[] {};
			int start = (hasSequence ? 1 : 0);
			// 判断是否存在属性值处理反调
			boolean hasHandler = (reflectPropertyHandler != null) ? true : false;
			// 存在反调，则将对象的属性和属性所在的顺序放入hashMap中，便于后面反调中通过属性调用
			if (hasHandler) {
				HashMap<String, Integer> propertyIndexMap = new HashMap<String, Integer>();
				for (int i = 0; i < methodLength; i++) {
					propertyIndexMap.put(properties[i].toLowerCase(), i + start);
				}
				reflectPropertyHandler.setPropertyIndexMap(propertyIndexMap);
			}
			for (int i = 0, n = datas.size(); i < n; i++) {
				rowObject = datas.get(i);
				if (null != rowObject) {
					// 第一行数据
					if (!inited) {
						realMethods = matchGetMethods(rowObject.getClass(), properties);
						inited = true;
					}
					List dataList = new ArrayList();
					if (hasSequence) {
						dataList.add(StringUtil.addLeftZero2Len(Long.toString(startSequence + i), maxLength));
					}
					for (int j = 0; j < methodLength; j++) {
						if (realMethods[j] != null) {
							dataList.add(realMethods[j].invoke(rowObject, params));
						} else {
							dataList.add(null);
						}
					}
					// 反调对数据值进行加工处理
					if (hasHandler) {
						reflectPropertyHandler.setRowIndex(i);
						reflectPropertyHandler.setRowList(dataList);
						reflectPropertyHandler.process();
						resultList.add(reflectPropertyHandler.getRowList());
					} else {
						resultList.add(dataList);
					}
				} else {
					if (logger.isDebugEnabled()) {
						logger.debug("BeanUtil.reflectBeansToList 方法,第:{}行数据为null,如果是sql查询请检查写法是否正确!", i);
					} else {
						err.println("BeanUtil.reflectBeansToList 方法,第:{" + i + "}行数据为null,如果是sql查询请检查写法是否正确!");
					}
					resultList.add(null);
				}
			}
		} catch (Exception e) {
			logger.error("反射Java Bean获取数据组装List集合异常!{}", e.getMessage());
			e.printStackTrace();
			throw e;
		}
		return resultList;
	}

	/**
	 * update 2020-12-2 支持多层对象反射取值
	 * 
	 * @todo 反射出单个对象中的属性并以对象数组返回
	 * @param serializable
	 * @param properties
	 * @param defaultValues
	 * @param reflectPropertyHandler
	 * @return
	 * @throws Exception
	 */
	public static Object[] reflectBeanToAry(Object serializable, String[] properties, Object[] defaultValues,
			ReflectPropertyHandler reflectPropertyHandler) {
		if (null == serializable || null == properties || properties.length == 0) {
			return null;
		}
		int methodLength = properties.length;
		Object[] result = new Object[methodLength];
		// 判断是否存在属性值处理反调
		boolean hasHandler = (reflectPropertyHandler != null) ? true : false;
		// 存在反调，则将对象的属性和属性所在的顺序放入hashMap中，便于后面反调中通过属性调用
		if (hasHandler) {
			HashMap<String, Integer> propertyIndexMap = new HashMap<String, Integer>();
			for (int i = 0; i < methodLength; i++) {
				propertyIndexMap.put(properties[i].toLowerCase(), i);
			}
			reflectPropertyHandler.setPropertyIndexMap(propertyIndexMap);
		}
		String[] fields;
		try {
			// 通过反射提取属性getMethod返回的数据值
			for (int i = 0; i < methodLength; i++) {
				if (properties[i] != null) {
					// 支持xxxx.xxx 子对象属性提取
					fields = properties[i].split("\\.");
					Object value = serializable;
					for (String field : fields) {
						value = getProperty(value, field.trim());
						if (value == null) {
							break;
						}
					}
					result[i] = value;
				}
			}
		} catch (Exception e) {
			throw new RuntimeException(e.getMessage());
		}
		// 默认值
		if (defaultValues != null) {
			int end = (defaultValues.length > methodLength) ? methodLength : defaultValues.length;
			for (int i = 0; i < end; i++) {
				if (result[i] == null) {
					result[i] = defaultValues[i];
				}
			}
		}
		// 反调对数据值进行加工处理
		if (hasHandler) {
			reflectPropertyHandler.setRowIndex(0);
			reflectPropertyHandler.setRowData(result);
			reflectPropertyHandler.process();
			return reflectPropertyHandler.getRowData();
		}
		return result;
	}

	/**
	 * @todo 利用java.lang.reflect并结合页面的property， 从对象中取出对应方法的值，组成一个List
	 * @param dataSet
	 * @param properties
	 * @param defaultValues
	 * @param reflectPropertyHandler
	 * @param hasSequence
	 * @param startSequence
	 * @return
	 * @throws Exception
	 */
	public static List<Object[]> reflectBeansToInnerAry(List dataSet, String[] properties, Object[] defaultValues,
			ReflectPropertyHandler reflectPropertyHandler, boolean hasSequence, int startSequence) {
		if (null == dataSet || dataSet.isEmpty() || null == properties || properties.length < 1) {
			return null;
		}
		// 数据的长度
		int maxLength = Integer.toString(dataSet.size()).length();
		List<Object[]> resultList = new ArrayList<Object[]>();
		try {
			int methodLength = properties.length;
			int defaultValueLength = (defaultValues == null) ? 0 : defaultValues.length;
			Method[] realMethods = null;
			boolean inited = false;
			Object rowObject = null;
			Object[] params = new Object[] {};
			int start = (hasSequence ? 1 : 0);
			// 判断是否存在属性值处理反调
			boolean hasHandler = (reflectPropertyHandler != null) ? true : false;
			// 存在反调，则将对象的属性和属性所在的顺序放入hashMap中，便于后面反调中通过属性调用
			if (hasHandler) {
				HashMap<String, Integer> propertyIndexMap = new HashMap<String, Integer>();
				for (int i = 0; i < methodLength; i++) {
					propertyIndexMap.put(properties[i].toLowerCase(), i + start);
				}
				reflectPropertyHandler.setPropertyIndexMap(propertyIndexMap);
			}
			// 逐行提取属性数据
			for (int i = 0, n = dataSet.size(); i < n; i++) {
				rowObject = dataSet.get(i);
				if (null != rowObject) {
					// 初始化属性对应getMethod的位置,提升反射的效率
					if (!inited) {
						realMethods = matchGetMethods(rowObject.getClass(), properties);
						inited = true;
					}
					Object[] dataAry = new Object[methodLength + start];
					// 存在流水列
					if (hasSequence) {
						dataAry[0] = StringUtil.addLeftZero2Len(Long.toString(startSequence + i), maxLength);
					}
					// 通过反射提取属性getMethod返回的数据值
					for (int j = 0; j < methodLength; j++) {
						if (null != realMethods[j]) {
							dataAry[start + j] = realMethods[j].invoke(rowObject, params);
							if (null == dataAry[start + j] && null != defaultValues) {
								dataAry[start + j] = (j >= defaultValueLength) ? null : defaultValues[j];
							}
						} else {
							if (defaultValues == null) {
								dataAry[start + j] = null;
							} else {
								dataAry[start + j] = (j >= defaultValueLength) ? null : defaultValues[j];
							}
						}
					}
					// 反调对数据值进行加工处理
					if (hasHandler) {
						reflectPropertyHandler.setRowIndex(i);
						reflectPropertyHandler.setRowData(dataAry);
						reflectPropertyHandler.process();
						resultList.add(reflectPropertyHandler.getRowData());
					} else {
						resultList.add(dataAry);
					}
				} else {
					if (logger.isDebugEnabled()) {
						logger.debug("BeanUtil.reflectBeansToInnerAry 方法,第:{}行数据为null,如果是sql查询请检查写法是否正确!", i);
					} else {
						err.println("BeanUtil.reflectBeansToInnerAry 方法,第:{" + i + "}行数据为null,如果是sql查询请检查写法是否正确!");
					}
					resultList.add(null);
				}
			}
		} catch (Exception e) {
			logger.error("反射Java Bean获取数据组装List集合异常!{}", e.getMessage());
			e.printStackTrace();
			throw new RuntimeException(e);
		}
		return resultList;
	}

	public static List reflectListToBean(TypeHandler typeHandler, List datas, String[] properties, Class voClass) {
		int[] indexs = null;
		if (properties != null && properties.length > 0) {
			indexs = new int[properties.length];
			for (int i = 0; i < indexs.length; i++) {
				indexs[i] = i;
			}
		}
		return reflectListToBean(typeHandler, datas, indexs, properties, voClass, true);
	}

	/**
	 * @todo 将二维数组映射到对象集合中
	 * @param typeHandler
	 * @param datas
	 * @param indexs
	 * @param properties
	 * @param voClass
	 * @return
	 * @throws Exception
	 */
	public static List reflectListToBean(TypeHandler typeHandler, List datas, int[] indexs, String[] properties,
			Class voClass) throws Exception {
		return reflectListToBean(typeHandler, datas, indexs, properties, voClass, true);
	}

	/**
	 * @todo 利用java.lang.reflect并结合页面的property， 从对象中取出对应方法的值，组成一个List
	 * @param typeHandler
	 * @param datas
	 * @param indexs
	 * @param properties
	 * @param voClass
	 * @param autoConvertType
	 * @return
	 */
	public static List reflectListToBean(TypeHandler typeHandler, List datas, int[] indexs, String[] properties,
			Class voClass, boolean autoConvertType) {
		if (null == datas || datas.isEmpty()) {
			return null;
		}
		if (null == properties || properties.length < 1 || null == voClass || null == indexs || indexs.length == 0
				|| properties.length != indexs.length) {
			throw new IllegalArgumentException("集合或属性名称数组为空,请检查参数信息!");
		}
		if (Modifier.isAbstract(voClass.getModifiers()) || Modifier.isInterface(voClass.getModifiers())) {
			throw new IllegalArgumentException("toClassType:" + voClass.getName() + " 是抽象类或接口,非法参数!");
		}
		List resultList = new ArrayList();
		Object cellData = null;
		String propertyName = null;
		try {
			Object rowObject = null;
			Object bean;
			boolean isArray = false;
			int meter = 0;
			Object[] rowArray;
			List rowList;
			int indexSize = indexs.length;
			Method[] realMethods = matchSetMethods(voClass, properties);
			String[] methodTypes = new String[indexSize];
			Class[] genericTypes = new Class[indexSize];
			Type[] types;
			// 自动适配属性的数据类型
			if (autoConvertType) {
				for (int i = 0; i < indexSize; i++) {
					if (null != realMethods[i]) {
						methodTypes[i] = realMethods[i].getParameterTypes()[0].getTypeName();
						types = realMethods[i].getGenericParameterTypes();
						if (types.length > 0) {
							if (types[0] instanceof ParameterizedType) {
								genericTypes[i] = (Class) ((ParameterizedType) types[0]).getActualTypeArguments()[0];
							}
						}
					}
				}
			}

			Iterator iter = datas.iterator();
			int index = 0;
			int size;
			while (iter.hasNext()) {
				rowObject = iter.next();
				if (rowObject != null) {
					bean = voClass.getDeclaredConstructor().newInstance();
					if (meter == 0) {
						if (rowObject instanceof Object[]) {
							isArray = true;
						}
					}
					if (isArray) {
						rowArray = (Object[]) rowObject;
						size = rowArray.length;
						for (int i = 0; i < indexSize; i++) {
							if (indexs[i] < size) {
								cellData = rowArray[indexs[i]];
								if (realMethods[i] != null && cellData != null) {
									propertyName = realMethods[i].getName();
									// 类型相同
									if (cellData.getClass().getTypeName().equals(methodTypes[i])) {
										realMethods[i].invoke(bean, cellData);
									} else {
										realMethods[i].invoke(bean,
												autoConvertType
														? convertType(typeHandler, cellData, methodTypes[i],
																genericTypes[i])
														: cellData);
									}
								}
							}
						}
					} else {
						rowList = (List) rowObject;
						size = rowList.size();
						for (int i = 0; i < indexSize; i++) {
							if (indexs[i] < size) {
								cellData = rowList.get(indexs[i]);
								if (realMethods[i] != null && cellData != null) {
									propertyName = realMethods[i].getName();
									if (cellData.getClass().getTypeName().equals(methodTypes[i])) {
										realMethods[i].invoke(bean, cellData);
									} else {
										realMethods[i].invoke(bean,
												autoConvertType
														? convertType(typeHandler, cellData, methodTypes[i],
																genericTypes[i])
														: cellData);
									}
								}
							}
						}
					}
					resultList.add(bean);
					meter++;
				} else {
					if (logger.isDebugEnabled()) {
						logger.debug("BeanUtil.reflectListToBean 方法,第:{}行数据为null,如果是sql查询请检查写法是否正确!", index);
					} else {
						err.println("BeanUtil.reflectListToBean 方法,第:{" + index + "}行数据为null,如果是sql查询请检查写法是否正确!");
					}
					resultList.add(null);
				}
				index++;
			}
		} catch (Exception e) {
			logger.error("将集合单元格数据:{} 反射到Java Bean的属性:{}过程异常!{}", cellData, propertyName, e.getMessage());
			e.printStackTrace();
			throw new RuntimeException(e);
		}
		return resultList;
	}

	public static void batchSetProperties(Collection voList, String[] properties, Object[] values,
			boolean autoConvertType) {
		batchSetProperties(voList, properties, values, autoConvertType, true);
	}

	/**
	 * @todo 批量对集合的属性设置相同的值
	 * @param voList
	 * @param properties
	 * @param values
	 * @param autoConvertType
	 * @param forceUpdate     强制更新
	 * @throws Exception
	 */
	public static void batchSetProperties(Collection voList, String[] properties, Object[] values,
			boolean autoConvertType, boolean forceUpdate) {
		if (null == voList || voList.isEmpty()) {
			return;
		}
		if (null == properties || properties.length < 1 || null == values || values.length < 1
				|| properties.length != values.length) {
			throw new IllegalArgumentException("集合或属性名称数组为空,请检查参数信息!");
		}
		try {
			int indexSize = properties.length;
			Method[] realMethods = null;
			String[] methodTypes = new String[indexSize];
			Class[] genericTypes = new Class[indexSize];
			Type[] types;
			Iterator iter = voList.iterator();
			Object bean;
			boolean inited = false;
			while (iter.hasNext()) {
				bean = iter.next();
				if (null != bean) {
					if (!inited) {
						realMethods = matchSetMethods(bean.getClass(), properties);
						if (autoConvertType) {
							for (int i = 0; i < indexSize; i++) {
								if (realMethods[i] != null) {
									methodTypes[i] = realMethods[i].getParameterTypes()[0].getTypeName();
									types = realMethods[i].getGenericParameterTypes();
									if (types.length > 0) {
										if (types[0] instanceof ParameterizedType) {
											genericTypes[i] = (Class) ((ParameterizedType) types[0])
													.getActualTypeArguments()[0];
										}
									}
								}
							}
						}
						inited = true;
					}
					for (int i = 0; i < indexSize; i++) {
						if (realMethods[i] != null && (forceUpdate || values[i] != null)) {
							realMethods[i].invoke(bean,
									autoConvertType ? convertType(null, values[i], methodTypes[i], genericTypes[i])
											: values[i]);
						}
					}
				}
			}
		} catch (Exception e) {
			logger.error("将集合数据反射到Java Bean过程异常!{}", e.getMessage());
			e.printStackTrace();
			throw new RuntimeException("将集合数据反射到Java Bean过程异常!{}" + e.getMessage(), e);
		}
	}

	/**
	 * @todo 对集合属性进行赋值
	 * @param voList
	 * @param properties
	 * @param values
	 * @param index
	 * @param autoConvertType
	 * @throws Exception
	 */
	public static void mappingSetProperties(Collection voList, String[] properties, List<Object[]> values, int[] index,
			boolean autoConvertType) throws Exception {
		mappingSetProperties(voList, properties, values, index, autoConvertType, true);
	}

	public static void mappingSetProperties(Collection voList, String[] properties, List<Object[]> values, int[] index,
			boolean autoConvertType, boolean forceUpdate) throws Exception {
		if (null == voList || voList.isEmpty()) {
			return;
		}
		if (null == properties || properties.length < 1 || null == values || values.get(0).length < 1
				|| properties.length != index.length) {
			throw new IllegalArgumentException("集合或属性名称数组为空,请检查参数信息!");
		}
		try {
			int indexSize = properties.length;
			Method[] realMethods = null;
			String[] methodTypes = new String[indexSize];
			Class[] genericTypes = new Class[indexSize];
			Type[] types;
			Iterator iter = voList.iterator();
			Object bean;
			boolean inited = false;
			int rowIndex = 0;
			Object[] rowData;
			while (iter.hasNext()) {
				if (rowIndex > values.size() - 1) {
					break;
				}
				rowData = values.get(rowIndex);
				bean = iter.next();
				if (null != bean) {
					if (!inited) {
						realMethods = matchSetMethods(bean.getClass(), properties);
						if (autoConvertType) {
							for (int i = 0; i < indexSize; i++) {
								if (realMethods[i] != null) {
									methodTypes[i] = realMethods[i].getParameterTypes()[0].getTypeName();
									types = realMethods[i].getGenericParameterTypes();
									if (types.length > 0) {
										if (types[0] instanceof ParameterizedType) {
											genericTypes[i] = (Class) ((ParameterizedType) types[0])
													.getActualTypeArguments()[0];
										}
									}
								}
							}
						}
						inited = true;
					}
					for (int i = 0; i < indexSize; i++) {
						if (realMethods[i] != null && (forceUpdate || rowData[index[i]] != null)) {
							realMethods[i].invoke(bean,
									autoConvertType
											? convertType(null, rowData[index[i]], methodTypes[i], genericTypes[i])
											: rowData[index[i]]);
						}
					}
				}
				rowIndex++;
			}
		} catch (Exception e) {
			logger.error("将集合数据反射到Java Bean过程异常!{}", e.getMessage());
			e.printStackTrace();
			throw e;
		}
	}

	/**
	 * @todo 通过源对象集合数据映射到新的对象以集合返回
	 * @param fromBeans   源对象数据集合
	 * @param fromProps   源对象的属性
	 * @param targetProps 目标对象的属性
	 * @param newClass    目标对象类型
	 * @return
	 * @throws Exception
	 */
	public static List mappingBeanSet(TypeHandler typeHandler, List fromBeans, String[] fromProps, String[] targetProps,
			Class newClass) throws Exception {
		if ((fromProps == null || fromProps.length == 0) && (targetProps == null || targetProps.length == 0)) {
			return mappingBeanSet(typeHandler, fromBeans, fromProps, targetProps, newClass, true);
		}
		return mappingBeanSet(typeHandler, fromBeans, fromProps, targetProps, newClass, false);
	}

	/**
	 * @todo 完成两个结合数据的复制
	 * @param fromBeans
	 * @param fromProps
	 * @param targetProps
	 * @param newClass
	 * @param autoMapping 是否自动匹配
	 * @return
	 * @throws Exception
	 */
	public static List mappingBeanSet(TypeHandler typeHandler, List fromBeans, String[] fromProps, String[] targetProps,
			Class newClass, boolean autoMapping) throws Exception {
		if (autoMapping == false) {
			List result = reflectBeansToList(fromBeans, fromProps == null ? targetProps : fromProps);
			return reflectListToBean(typeHandler, result, targetProps == null ? fromProps : targetProps, newClass);
		}
		// 获取set方法
		String[] properties = matchSetMethodNames(newClass);
		String[] getProperties = new String[properties.length];
		HashMap<String, Integer> matchIndex = new HashMap<String, Integer>();
		if (targetProps != null && fromProps != null) {
			for (int i = 0; i < targetProps.length; i++) {
				matchIndex.put(targetProps[i].toLowerCase(), i);
			}
			Integer index;
			for (int i = 0; i < properties.length; i++) {
				index = matchIndex.get(properties[i].toLowerCase());
				if (index == null || index >= fromProps.length) {
					getProperties[i] = properties[i];
				} else {
					getProperties[i] = fromProps[index];
				}
			}
		} else {
			getProperties = properties;
		}
		List result = reflectBeansToList(fromBeans, getProperties);
		return reflectListToBean(typeHandler, result, properties, newClass);
	}

	public static String[] matchSetMethodNames(Class voClass) {
		return matchMethodNames(voClass, false);
	}

	private static String[] matchMethodNames(Class voClass, boolean isGet) {
		Method[] methods = voClass.getMethods();
		int methodCnt = methods.length;
		List<String> methodAry = new ArrayList();
		String methodName;
		Method method;
		for (int i = 0; i < methodCnt; i++) {
			method = methods[i];
			methodName = method.getName();
			if (isGet) {
				if ((methodName.startsWith("get") || methodName.startsWith("is"))
						&& !void.class.equals(method.getReturnType()) && method.getParameterTypes().length == 0
						&& !methodName.toLowerCase().equals("getclass")) {
					methodAry.add(StringUtil.firstToLowerCase(methodName.replaceFirst("get|is", "")));
				}
			} else {
				if (methodName.startsWith("set") && void.class.equals(method.getReturnType())
						&& method.getParameterTypes().length == 1) {
					methodAry.add(StringUtil.firstToLowerCase(methodName.replaceFirst("set", "")));
				}
			}
		}
		String[] result = new String[methodAry.size()];
		methodAry.toArray(result);
		return result;
	}

	/**
	 * @todo 根据方法名称以及参数数量获取类的具体方法
	 * @param beanClass
	 * @param methodName
	 * @param argLength
	 * @return
	 */
	public static Method getMethod(Class beanClass, String methodName, int argLength) {
		Method[] methods = beanClass.getMethods();
		int methodArgsLength;
		for (Method method : methods) {
			methodArgsLength = 0;
			if (method.getParameterTypes() != null) {
				methodArgsLength = method.getParameterTypes().length;
			}
			if (method.getName().equalsIgnoreCase(methodName) && methodArgsLength == argLength) {
				return method;
			}
		}
		return null;
	}

	/**
	 * @todo 判断对象是否是基本数据类型对象
	 * @param clazz
	 * @return
	 * @throws Exception
	 */
	public static boolean isBaseDataType(Class clazz) throws Exception {
		return (clazz.equals(String.class) || clazz.equals(Integer.class) || clazz.equals(Byte.class)
				|| clazz.equals(Long.class) || clazz.equals(Double.class) || clazz.equals(Float.class)
				|| clazz.equals(Character.class) || clazz.equals(Short.class) || clazz.equals(BigDecimal.class)
				|| clazz.equals(BigInteger.class) || clazz.equals(Boolean.class) || clazz.equals(Date.class)
				|| clazz.equals(Timestamp.class) || clazz.isPrimitive());
	}

	/**
	 * @TODO 代替PropertyUtil 和BeanUtils的setProperty方法
	 * @param bean
	 * @param property
	 * @param value
	 * @throws Exception
	 */
	public static void setProperty(Object bean, String property, Object value) throws Exception {
		String key = bean.getClass().getName().concat(":set").concat(property);
		// 利用缓存提升方法匹配效率
		Method method = setMethods.get(key);
		if (method == null) {
			method = matchSetMethods(bean.getClass(), new String[] { property })[0];
			if (method == null) {
				throw new Exception(bean.getClass().getName() + " 没有对应的:" + property);
			}
			setMethods.put(key, method);
		}
		// 将数据类型进行转换再赋值
		String type = method.getParameterTypes()[0].getTypeName();
		Type[] types = method.getGenericParameterTypes();
		Class genericType = null;
		if (types.length > 0) {
			if (types[0] instanceof ParameterizedType) {
				genericType = (Class) ((ParameterizedType) types[0]).getActualTypeArguments()[0];
			}
		}
		method.invoke(bean, convertType(null, value, type, genericType));
	}

	/**
	 * @TODO 代替BeanUtils.getProperty 方法
	 * @param bean
	 * @param property
	 * @return
	 * @throws Exception
	 */
	public static Object getProperty(Object bean, String property) throws Exception {
		String key = bean.getClass().getName().concat(":get").concat(property);
		// 利用缓存提升方法匹配效率
		Method method = getMethods.get(key);
		if (method == null) {
			method = matchGetMethods(bean.getClass(), new String[] { property })[0];
			if (method == null) {
				return null;
			}
			getMethods.put(key, method);
		}
		return method.invoke(bean);
	}

	/**
	 * @TODO 为loadByIds提供Entity集合封装,便于将调用方式统一
	 * @param <T>
	 * @param entityMeta
	 * @param voClass
	 * @param ids
	 * @return
	 */
	public static <T extends Serializable> List<T> wrapEntities(TypeHandler typeHandler, EntityMeta entityMeta,
			Class<T> voClass, Object... ids) {
		List<T> entities = new ArrayList<T>();
		Set<Object> repeat = new HashSet<Object>();
		try {
			// 获取主键的set方法
			Method method = BeanUtil.matchSetMethods(voClass, entityMeta.getIdArray())[0];
			String typeName = method.getParameterTypes()[0].getTypeName();
			Type[] types = method.getGenericParameterTypes();
			Class genericType = null;
			if (types.length > 0) {
				if (types[0] instanceof ParameterizedType) {
					genericType = (Class) ((ParameterizedType) types[0]).getActualTypeArguments()[0];
				}
			}
			T bean;
			for (Object id : ids) {
				// 去除重复
				if (!repeat.contains(id)) {
					bean = voClass.getDeclaredConstructor().newInstance();
					method.invoke(bean, BeanUtil.convertType(typeHandler, id, typeName, genericType));
					entities.add(bean);
					repeat.add(id);
				}
			}
		} catch (Exception e) {
			logger.error("将集合数据反射到Java Bean过程异常!{}", e.getMessage());
			throw new RuntimeException(e);
		}
		return entities;
	}

	/**
	 * @TODO 获取VO对应的Class
	 * @param entityClass
	 * @return
	 */
	public static Class getEntityClass(Class entityClass) {
		// update 2020-9-16
		// 主要规避VO对象{{}}模式初始化，导致Class获取变成了内部类(双括号实例化modifiers会等于0)
		if (entityClass == null || entityClass.getModifiers() != 0) {
			return entityClass;
		}
		Class realEntityClass = entityClass;
		// 通过逐层递归来判断是否SqlToy annotation注解所规定的关联数据库的实体类
		// 即@Entity 注解的抽象类
		boolean isEntity = realEntityClass.isAnnotationPresent(SqlToyEntity.class);
		while (!isEntity) {
			realEntityClass = realEntityClass.getSuperclass();
			if (realEntityClass == null) {
				break;
			}
			isEntity = realEntityClass.isAnnotationPresent(SqlToyEntity.class);
		}
		if (isEntity) {
			return realEntityClass;
		}
		return entityClass;
	}

	/**
	 * @TODO 对常规类型进行转换，超出部分由自定义类型处理器完成(或配置类型完全一致)
	 * @param values
	 * @param type   (已经小写)
	 * @return
	 */
	private static Object convertArray(Object values, String type) {
		// 类型完全一致
		if (type == null || type.equals(values.getClass().getTypeName().toLowerCase())) {
			return values;
		}
		Object[] array;
		int index = 0;
		if (type.contains("integer") && !(values instanceof Integer[])) {
			array = (Object[]) values;
			Integer[] result = new Integer[array.length];
			for (Object obj : array) {
				if (obj != null) {
					result[index] = new BigDecimal(obj.toString()).intValue();
				}
				index++;
			}
			return result;
		}
		if (type.contains("long") && !(values instanceof Long[])) {
			array = (Object[]) values;
			Long[] result = new Long[array.length];
			for (Object obj : array) {
				if (obj != null) {
					result[index] = new BigDecimal(obj.toString()).longValue();
				}
				index++;
			}
			return result;
		}
		if (type.contains("bigdecimal") && !(values instanceof BigDecimal[])) {
			array = (Object[]) values;
			BigDecimal[] result = new BigDecimal[array.length];
			for (Object obj : array) {
				if (obj != null) {
					result[index] = new BigDecimal(obj.toString());
				}
				index++;
			}
			return result;
		}
		if (type.contains("double") && !(values instanceof Double[])) {
			array = (Object[]) values;
			Double[] result = new Double[array.length];
			for (Object obj : array) {
				if (obj != null) {
					result[index] = new BigDecimal(obj.toString()).doubleValue();
				}
				index++;
			}
			return result;
		}
		if (type.contains("float") && !(values instanceof Float[])) {
			array = (Object[]) values;
			Float[] result = new Float[array.length];
			for (Object obj : array) {
				if (obj != null) {
					result[index] = new BigDecimal(obj.toString()).floatValue();
				}
				index++;
			}
			return result;
		}
		// update 2021-01-29 修复integer中包含int导致类型匹配错误
		// if (type.contains("int") && !(values instanceof int[]))
		if ((type.contains("int") && !type.contains("integer")) && !(values instanceof int[])) {
			array = (Object[]) values;
			int[] result = new int[array.length];
			for (Object obj : array) {
				if (obj != null) {
					result[index] = new BigDecimal(obj.toString()).intValue();
				}
				index++;
			}
			return result;
		}
		return values;
	}
}
