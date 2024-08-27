/*
 * Copyright 2017-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.serialization

import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlin.reflect.*

/**
 * The main entry point to the serialization process.
 * Applying [Serializable] to the Kotlin class instructs the serialization plugin to automatically generate implementation of [KSerializer]
 * for the current class, that can be used to serialize and deserialize the class.
 * The generated serializer can be accessed with `T.serializer()` extension function on the class companion,
 * both are generated by the plugin as well.
 *
 * ```
 * @Serializable
 * class MyData(val myData: AnotherData, val intProperty: Int, ...)
 *
 * // Produces JSON string using the generated serializer
 * val jsonString = Json.encodeToJson(MyData.serializer(), instance)
 * ```
 *
 * Additionally, the user-defined serializer can be specified using [with] parameter:
 * ```
 * @Serializable(with = MyAnotherDataCustomSerializer::class)
 * class MyAnotherData(...)
 *
 * MyAnotherData.serializer() // <- returns MyAnotherDataCustomSerializer
 * ```
 *
 * To continue generating the implementation of [KSerializer] using the plugin, specify the [KeepGeneratedSerializer] annotation.
 * In this case, the serializer will be available via `generatedSerializer()` function, and will also be used in the heirs.
 *
 * For annotated properties, specifying [with] parameter is mandatory and can be used to override
 * serializer on the use-site without affecting the rest of the usages:
 * ```
 * @Serializable // By default is serialized as 3 byte components
 * class RgbPixel(val red: Short, val green: Short, val blue: Short)
 *
 * @Serializable
 * class RgbExample(
 *     @Serializable(with = RgbAsHexString::class) p1: RgpPixel, // Serialize as HEX string, e.g. #FFFF00
 *     @Serializable(with = RgbAsSingleInt::class) p2: RgpPixel, // Serialize as single integer, e.g. 16711680
 *     p3: RgpPixel // Serialize as 3 short components, e.g. { "red": 255, "green": 255, "blue": 0 }
 * )
 * ```
 * In this example, each pixel will be serialized using different data representation.
 *
 * For classes with generic type parameters, `serializer()` function requires one additional argument per each generic type parameter:
 * ```
 * @Serializable
 * class Box<T>(value: T)
 *
 * Box.serializer() // Doesn't compile
 * Box.serializer(Int.serializer()) // Returns serializer for Box<Int>
 * Box.serializer(Box.serializer(Int.serializer())) // Returns serializer for Box<Box<Int>>
 * ```
 *
 * ### Implementation details
 *
 * In order to generate `serializer` function that is not a method on the particular instance, the class should have a companion object, either named or unnamed.
 * Companion object is generated by the plugin if it is not declared, effectively exposing both companion and `serializer()` method to class ABI.
 * If companion object already exists, only `serializer` method will be generated.
 *
 * @see UseSerializers
 * @see Serializer
 * @see KeepGeneratedSerializer
 */
@MustBeDocumented
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.CLASS, AnnotationTarget.TYPE)
//@Retention(AnnotationRetention.RUNTIME) // Runtime is the default retention, also see KT-41082
public annotation class Serializable(
    val with: KClass<out KSerializer<*>> = KSerializer::class // Default value indicates that auto-generated serializer is used
)

/**
 * The meta-annotation for adding [Serializable] behaviour to user-defined annotations.
 *
 * Applying [MetaSerializable] to the annotation class `A` instructs the serialization plugin to treat annotation A
 * as [Serializable]. In addition, all annotations marked with [MetaSerializable] are saved in the generated [SerialDescriptor]
 * as if they are annotated with [SerialInfo].
 *
 * ```
 * @MetaSerializable
 * @Target(AnnotationTarget.CLASS)
 * annotation class MySerializable(val data: String)
 *
 * @MySerializable("some_data")
 * class MyData(val myData: AnotherData, val intProperty: Int, ...)
 *
 * val serializer = MyData.serializer()
 * serializer.descriptor.annotations.filterIsInstance<MySerializable>().first().data // <- returns "some_data"
 * ```
 *
 * @see Serializable
 * @see SerialInfo
 * @see UseSerializers
 * @see Serializer
 */
@MustBeDocumented
@Target(AnnotationTarget.ANNOTATION_CLASS)
//@Retention(AnnotationRetention.RUNTIME) // Runtime is the default retention, also see KT-41082
@ExperimentalSerializationApi
public annotation class MetaSerializable

/**
 * Instructs the serialization plugin to turn this class into serializer for specified class [forClass].
 * However, it would not be used automatically. To apply it on particular class or property,
 * use [Serializable] or [UseSerializers], or [Contextual] with runtime registration.
 *
 * `@Serializer(forClass)` is experimental and unstable feature that can be changed in future releases.
 * Changes may include additional constraints on classes and objects marked with this annotation,
 * behavioural changes and even serialized shape of the class.
 */
@MustBeDocumented
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
@ExperimentalSerializationApi
public annotation class Serializer(
    val forClass: KClass<*> //  target class to create serializer for
)

/**
 * Overrides the name of a class or a property in the corresponding [SerialDescriptor].
 * Names and serial names are used by text-based serial formats in order to encode the name of the class or
 * the name of the property, e.g. by `Json`.
 *
 * By default, [SerialDescriptor.serialName] and [SerialDescriptor.getElementName]
 * are associated with fully qualified name of the target class and the name of the property respectively.
 * Applying this annotation changes the visible name to the given [value]:
 *
 * ```
 * package foo
 *
 * @Serializable // RegularName.serializer().descriptor.serialName is "foo.RegularName"
 * class RegularName(val myInt: Int)
 *
 * @Serializable
 * @SerialName("CustomName") // Omit package from name that is used for diagnostic and polymorphism
 * class CustomName(@SerialName("int") val myInt: Int)
 *
 * // Prints "{"myInt":42}"
 * println(Json.encodeToString(RegularName(42)))
 * // Prints "{"int":42}"
 * println(Json.encodeToString(CustomName(42)))
 * ```
 *
 * If a name of class or property is overridden with this annotation, original source code name is not available for the library.
 * Tools like `JsonNamingStrategy` and `ProtoBufSchemaGenerator` would see and transform [value] from [SerialName] annotation.
 */
@MustBeDocumented
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.CLASS)
// @Retention(AnnotationRetention.RUNTIME) still runtime, but KT-41082
public annotation class SerialName(val value: String)

/**
 * Indicates that property must be present during deserialization process, despite having a default value.
 */
@MustBeDocumented
@Target(AnnotationTarget.PROPERTY)
// @Retention(AnnotationRetention.RUNTIME) still runtime, but KT-41082
public annotation class Required

/**
 * Marks this property invisible for the whole serialization process, including [serial descriptors][SerialDescriptor].
 * Transient properties must have default values.
 */
@MustBeDocumented
@Target(AnnotationTarget.PROPERTY)
// @Retention(AnnotationRetention.RUNTIME) still runtime, but KT-41082
public annotation class Transient

/**
 * Controls whether the target property is serialized when its value is equal to a default value,
 * regardless of the format settings.
 * Does not affect decoding and deserialization process.
 *
 * Example of usage:
 * ```
 * @Serializable
 * data class Foo(
 *     @EncodeDefault(ALWAYS) val a: Int = 42,
 *     @EncodeDefault(NEVER) val b: Int = 43,
 *     val c: Int = 44
 * )
 *
 * Json { encodeDefaults = false }.encodeToString((Foo()) // {"a": 42}
 * Json { encodeDefaults = true }.encodeToString((Foo())  // {"a": 42, "c":44}
 * ```
 *
 * @see EncodeDefault.Mode.ALWAYS
 * @see EncodeDefault.Mode.NEVER
 */
@MustBeDocumented
@Target(AnnotationTarget.PROPERTY)
@ExperimentalSerializationApi
public annotation class EncodeDefault(val mode: Mode = Mode.ALWAYS) {
    /**
     * Strategy for the [EncodeDefault] annotation.
     */
    @ExperimentalSerializationApi
    public enum class Mode {
        /**
         * Configures serializer to always encode the property, even if its value is equal to its default.
         * For annotated properties, format settings are not taken into account and
         * [CompositeEncoder.shouldEncodeElementDefault] is not invoked.
         */
        ALWAYS,

        /**
         * Configures serializer not to encode the property if its value is equal to its default.
         * For annotated properties, format settings are not taken into account and
         * [CompositeEncoder.shouldEncodeElementDefault] is not invoked.
         */
        NEVER
    }
}

/**
 * Meta-annotation that commands the compiler plugin to handle the annotation as serialization-specific.
 * Serialization-specific annotations are preserved in the [SerialDescriptor] and can be retrieved
 * during serialization process with [SerialDescriptor.getElementAnnotations] for properties annotations
 * and [SerialDescriptor.annotations] for class annotations.
 *
 * It is recommended to explicitly specify target for serial info annotations, whether it is [AnnotationTarget.PROPERTY], [AnnotationTarget.CLASS], or both.
 * Keep in mind that Kotlin compiler prioritizes [function parameter target][AnnotationTarget.VALUE_PARAMETER] over [property target][AnnotationTarget.PROPERTY],
 * so serial info annotations used on constructor-parameters-as-properties without explicit declaration-site or use-site target are not preserved.
 */
@MustBeDocumented
@Target(AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.BINARY)
@ExperimentalSerializationApi
public annotation class SerialInfo

/**
 * Meta-annotation that commands the compiler plugin to handle the annotation as serialization-specific.
 * Serialization-specific annotations are preserved in the [SerialDescriptor] and can be retrieved
 * during serialization process with [SerialDescriptor.getElementAnnotations].
 *
 * In contrary to regular [SerialInfo], this one makes annotations inheritable:
 * If class X marked as [Serializable] has any of its supertypes annotated with annotation A that has `@InheritableSerialInfo` on it,
 * A appears in X's [SerialDescriptor] even if X itself is not annotated.
 * It is possible to use A multiple times on different supertypes. Resulting X's [SerialDescriptor.annotations] would still contain
 * only one instance of A.
 * Note that if A has any arguments, their values should be the same across all hierarchy. Otherwise, a compilation error
 * would be reported by the plugin.
 *
 * Example:
 * ```
 * @InheritableSerialInfo
 * annotation class A(val value: Int)
 *
 * @A(1) // Annotation can also be inherited from interfaces
 * interface I
 *
 * @Serializable
 * @A(1) // Argument value is the same as in I, no compiler error
 * abstract class Base: I
 *
 * @Serializable
 * class Derived: Base()
 *
 * // This function returns 1.
 * fun foo(): Int = Derived.serializer().descriptor.annotations.filterIsInstance<A>().single().value
 * ```
 */
@MustBeDocumented
@Target(AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.BINARY)
@ExperimentalSerializationApi
public annotation class InheritableSerialInfo

/**
 * Instructs the plugin to use [ContextualSerializer] on a given property or type.
 * Context serializer is usually used when serializer for type can only be found in runtime.
 * It is also possible to apply [ContextualSerializer] to every property of the given type,
 * using file-level [UseContextualSerialization] annotation.
 *
 * @see ContextualSerializer
 * @see UseContextualSerialization
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.TYPE)
@Retention(AnnotationRetention.BINARY)
public annotation class Contextual

/**
 * Instructs the plugin to use [ContextualSerializer] for every type in the current file that is listed in the [forClasses].
 *
 * @see Contextual
 * @see ContextualSerializer
 */
@Target(AnnotationTarget.FILE)
@Retention(AnnotationRetention.BINARY)
public annotation class UseContextualSerialization(vararg val forClasses: KClass<*>)

/**
 *  Adds [serializerClasses] to serializers resolving process inside the plugin.
 *  Each of [serializerClasses] must implement [KSerializer].
 *
 *  Inside the file with this annotation, for each given property
 *  of type `T` in some serializable class, this list would be inspected for the presence of `KSerializer<T>`.
 *  If such serializer is present, it would be used instead of default.
 *
 *  Main use-case for this annotation is not to write @Serializable(with=SomeSerializer::class)
 *  on each property with custom serializer.
 *
 *  Serializers from this list have higher priority than default, but lesser priority than
 *  serializers defined on the property itself, such as [Serializable] (with=...) or [Contextual].
 */
@Target(AnnotationTarget.FILE)
@Retention(AnnotationRetention.BINARY)
public annotation class UseSerializers(vararg val serializerClasses: KClass<out KSerializer<*>>)

/**
 * Instructs the serialization plugin to use [PolymorphicSerializer] on an annotated property or type usage.
 * When used on class, replaces its serializer with [PolymorphicSerializer] everywhere.
 *
 * This annotation is applied automatically to interfaces and serializable abstract classes
 * and can be applied to open classes in addition to [Serializable] for the sake of simplicity.
 *
 * Does not affect sealed classes, because they are gonna be serialized with subclasses automatically
 * with special compiler plugin support which would be added later.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.TYPE, AnnotationTarget.CLASS)
//@Retention(AnnotationRetention.RUNTIME) // Runtime is the default retention, also see KT-41082
public annotation class Polymorphic

/**
 * Instructs the serialization plugin to keep automatically generated implementation of [KSerializer]
 * for the current class if a custom serializer is specified at the same time `@Serializable(with=SomeSerializer::class)`.
 *
 * Automatically generated serializer is available via `generatedSerializer()` function in companion object of serializable class.
 *
 * Keeping generated serializers allow to use plugin generated serializer in inheritors even if custom serializer is specified.
 *
 * Used only with annotation [Serializable] with the specified argument [Serializable.with], e.g. `@Serializable(with=SomeSerializer::class)`.
 *
 * Annotation is not allowed on classes involved in polymorphic serialization:
 * interfaces, sealed classes, abstract classes, classes marked by [Polymorphic].
 *
 * A compiler version `2.0.20` or higher is required.
 */
@ExperimentalSerializationApi
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public annotation class KeepGeneratedSerializer

/**
 * Marks declarations that are still **experimental** in kotlinx.serialization, which means that the design of the
 * corresponding declarations has open issues which may (or may not) lead to their changes in the future.
 * Roughly speaking, there is a chance that those declarations will be deprecated in the near future or
 * the semantics of their behavior may change in some way that may break some code.
 *
 * By default, the following categories of API are experimental:
 *
 * * Writing 3rd-party serialization formats
 * * Writing non-trivial custom serializers
 * * Implementing [SerialDescriptor] interfaces
 * * Not-yet-stable serialization formats that require additional polishing
 */
@MustBeDocumented
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION, AnnotationTarget.TYPEALIAS)
@RequiresOptIn(level = RequiresOptIn.Level.WARNING)
public annotation class ExperimentalSerializationApi

/**
 * Public API marked with this annotation is effectively **internal**, which means
 * it should not be used outside of `kotlinx.serialization`.
 * Signature, semantics, source and binary compatibilities are not guaranteed for this API
 * and will be changed without any warnings or migration aids.
 * If you cannot avoid using internal API to solve your problem, please report your use-case to serialization's issue tracker.
 */
@MustBeDocumented
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION, AnnotationTarget.TYPEALIAS)
@RequiresOptIn(level = RequiresOptIn.Level.ERROR)
public annotation class InternalSerializationApi
