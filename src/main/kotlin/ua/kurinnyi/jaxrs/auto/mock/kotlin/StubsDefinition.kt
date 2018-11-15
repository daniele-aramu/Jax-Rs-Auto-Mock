package ua.kurinnyi.jaxrs.auto.mock.kotlin

import ua.kurinnyi.jaxrs.auto.mock.Utils
import ua.kurinnyi.jaxrs.auto.mock.body.BodyProvider
import ua.kurinnyi.jaxrs.auto.mock.body.FileBodyProvider
import ua.kurinnyi.jaxrs.auto.mock.body.JacksonBodyProvider
import ua.kurinnyi.jaxrs.auto.mock.body.JerseyInternalBodyProvider
import ua.kurinnyi.jaxrs.auto.mock.httpproxy.NothingMatchedProxyConfiguration
import ua.kurinnyi.jaxrs.auto.mock.httpproxy.ProxyConfiguration
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlin.reflect.KClass

interface StubsDefinition {
    fun getStubs(context: StubDefinitionContext): List<MethodStub>
}

class StubDefinitionContext(val proxyConfiguration: ProxyConfiguration) {
    internal val stubs: MutableList<MethodStub> = mutableListOf()
    internal var proxyBypassPath:String? = null
    internal var shouldBypassWhenNothingMatched:Boolean = false


    fun bypassAnyNotMatched(path: String) {
        shouldBypassWhenNothingMatched = true
        proxyBypassPath = path
    }

    fun bypassAnyNotMatched() {
        shouldBypassWhenNothingMatched = true
    }


    fun createStubs(definitions: StubDefinitionContext.() -> Unit): List<MethodStub> {
        definitions(this)
        return stubs.toList()
    }

    fun <RESOURCE : Any> forClass(clazz: KClass<RESOURCE>, definitions: ClazzStubDefinitionContext<RESOURCE>.() -> Unit) {
        if (shouldBypassWhenNothingMatched){
            proxyConfiguration.addClass(clazz.java.name, proxyBypassPath)
        }
        definitions(ClazzStubDefinitionContext(clazz.java, this))
    }
}

class ClazzStubDefinitionContext<RESOURCE>(private val clazz: Class<RESOURCE>, private val context: StubDefinitionContext) {
    private var tempArgList: List<MethodStub.ArgumentMatcher> = listOf()
    private var methodStubs: List<MethodStub> = listOf()

    fun bypassAnyNotMatched(path: String) {
        context.proxyConfiguration.addClass(clazz.name, path)
    }

    fun bypassAnyNotMatched() {
        context.proxyConfiguration.addClass(clazz.name, null)
    }

    fun <RESULT> whenRequest(methodCall: RESOURCE.() -> RESULT): MethodStubDefinitionRequestContext<RESULT> {
        methodStubs = listOf()
        val instance = Proxy.newProxyInstance(clazz.classLoader, arrayOf(clazz)) { proxy, method, args ->
            checkAllArgumentsSetUp(args, method)
            methodStubs += MethodStub(clazz, method, tempArgList)
            tempArgList = listOf()
            null
        } as RESOURCE
        methodCall(instance)
        context.stubs.addAll(methodStubs)
        return MethodStubDefinitionRequestContext(methodStubs)
    }

    private fun checkAllArgumentsSetUp(args: Array<Any>?, method: Method) {

        if (args != null && tempArgList.size < args.size) {
            throw IllegalArgumentException("Not all parameters of method: ${method.name} are " +
                    "called with matchers. Use match methods like 'eq','any' etc for all the parameters")
        }
    }

    fun <ARGUMENT> eq(argument: ARGUMENT): ARGUMENT = matchNullable { it == argument }

    fun <ARGUMENT> notEq(argument: ARGUMENT): ARGUMENT = matchNullable { it != argument }

    fun <ARGUMENT> any(): ARGUMENT = matchNullable { true }

    fun <ARGUMENT> isNull(): ARGUMENT = matchNullable { it == null }

    fun <ARGUMENT> notNull(): ARGUMENT = matchNullable { it != null }

    fun <ARGUMENT> matchNullable(predicate: (ARGUMENT?) -> Boolean): ARGUMENT {
        val castedPredicate = { arg: Any? -> predicate(arg as ARGUMENT) }
        tempArgList += MethodStub.ArgumentMatcher(MethodStub.MatchType.MATCH, castedPredicate)
        return null as ARGUMENT
    }

    fun <ARGUMENT> match(predicate: (ARGUMENT) -> Boolean): ARGUMENT = matchNullable { it != null && predicate(it) }

    fun <ARGUMENT> bodyMatch(predicate: (String) -> Boolean): ARGUMENT {
        val castedPredicate = { arg: Any? -> predicate(arg as String) }
        tempArgList += MethodStub.ArgumentMatcher(MethodStub.MatchType.BODY_MATCH, castedPredicate)
        return null as ARGUMENT
    }

    fun <ARGUMENT> bodyMatchRegex(regex: String): ARGUMENT =
            bodyMatch { regex.toRegex().matches(it) }

    fun <ARGUMENT> bodyJson(body: String): ARGUMENT =
            bodyMatch { Utils.trimToSingleSpaces(body) == Utils.trimToSingleSpaces(it) }
}

class MethodStubDefinitionRequestContext<RESULT>(private val methodStubs: List<MethodStub>) {

    infix fun with(additionalRequestDefinition: MethodStubDefinitionRequestParamsContext.() -> Unit)
            : MethodStubDefinitionRequestContext<RESULT> {
        additionalRequestDefinition(MethodStubDefinitionRequestParamsContext(methodStubs))
        return this
    }

    infix fun thenResponse(responseDefinition: MethodStubDefinitionResponseContext<RESULT>.() -> Unit) {
        responseDefinition(MethodStubDefinitionResponseContext(methodStubs))
    }

}

class MethodStubDefinitionRequestParamsContext(private val methodStubs: List<MethodStub>) {

    fun header(name: String, value: HeaderValue) {
        methodStubs.forEach { it.requestHeaders.add(MethodStub.HeaderParameter(name, value.matcher)) }
    }

    class HeaderValue private constructor(val matcher: MethodStub.ArgumentMatcher) {
        companion object {
            fun forMatcher(matcher: MethodStub.ArgumentMatcher): HeaderValue {
                return HeaderValue(matcher)
            }
        }
    }

    fun eq(value: String): HeaderValue = matchNullable { it == value }

    fun match(predicate: (String) -> Boolean): HeaderValue = matchNullable { it != null && predicate(it) }

    fun matchNullable(predicate: (String?) -> Boolean): HeaderValue {
        val castedPredicate = { arg: Any? -> predicate(arg as String?) }
        return HeaderValue.forMatcher(MethodStub.ArgumentMatcher(MethodStub.MatchType.MATCH, castedPredicate))
    }

    fun notEq(value: String): HeaderValue = matchNullable { it != value }

    fun isNull(): HeaderValue = matchNullable { it == null }

    fun notNull(): HeaderValue = matchNullable { it != null }

    fun matchRegex(regex: String): HeaderValue = matchNullable { it != null && regex.toRegex().matches(it) }
}

class MethodStubDefinitionResponseContext<RESPONSE>(private val methodStubs: List<MethodStub>) {

    fun code(code: Int): Unit = methodStubs.forEach { it.code = code }

    fun body(body: RESPONSE) = bodyProvider{ body }

    fun bodyJson(bodyProvider: BodyProvider, body: String) = methodStubs.forEach { it.bodyJson = body; it.bodyJsonProvider = bodyProvider }

    fun bodyProvider(bodyProvider: (Array<Any?>) -> RESPONSE?) = methodStubs.forEach { it.bodyProvider = bodyProvider }

    fun proxyTo(path: String) = methodStubs.forEach { it.proxyPath = path }

    fun header(headerName: String, headerValue: String) = methodStubs.forEach {
        it.responseHeaders[headerName] = headerValue
    }
}
typealias BY_JACKSON = JacksonBodyProvider
typealias BY_JERSEY = JerseyInternalBodyProvider
typealias FROM_FILE = FileBodyProvider
