package ua.kurinnyi.jaxrs.auto.mock

import org.apache.catalina.Context
import org.apache.catalina.startup.Tomcat
import org.apache.tomcat.util.descriptor.web.FilterDef
import org.apache.tomcat.util.descriptor.web.FilterMap
import org.glassfish.jersey.server.ResourceConfig
import org.glassfish.jersey.servlet.ServletContainer
import org.reflections.Reflections
import ua.kurinnyi.jaxrs.auto.mock.kotlin.AutoDiscoveryOfStubDefinitions
import ua.kurinnyi.jaxrs.auto.mock.kotlin.KotlinMethodStubsLoader
import ua.kurinnyi.jaxrs.auto.mock.kotlin.StubsDefinition
import ua.kurinnyi.jaxrs.auto.mock.yaml.ResponseFromStubCreator
import ua.kurinnyi.jaxrs.auto.mock.yaml.YamlMethodStubsLoader
import java.io.File


class StubServer {

    private val reflections = Reflections()

    private var port = 8080
    private var useJerseyDeserialization = false
    private val packagesToScan = mutableListOf<String>()
    private val classesToRegister = HashSet<Class<*>>()
    private val stubDefinitions  = mutableListOf<StubsDefinition>()
    private var autoDiscoveryOfStubDefinitions  = true

    fun setPort(port: Int): StubServer = this.apply{
        this.port = port
    }

    fun addPackageToScanForPorviders(packageName:String):StubServer = this.apply {
        packagesToScan.add(packageName)
    }

    fun useJerseyDeserializationForYamlStubs():StubServer = this.apply {
        useJerseyDeserialization = true
    }

    fun addProviderClassToRegister(clazz: Class<*>):StubServer = this.apply {
        classesToRegister.add(clazz)
    }

    fun addStubDefinition(stubDefinition: StubsDefinition):StubServer = this.apply {
        stubDefinitions.add(stubDefinition)
    }

    fun disableAutoDiscoveryOfStubDefinition():StubServer = this.apply {
        autoDiscoveryOfStubDefinitions = false
    }

    fun start() {
        val tomcat = Tomcat()
        tomcat.setPort(port)

        val context:Context = tomcat.addWebapp("/", File(".").absolutePath)

        val resourceLoader = ResourceLoaderOfProxyInstances(getInterfacesToStub(), instantiateDependencies())
        resourceLoader.register(JerseyInternalsFilter::class.java)
        resourceLoader.register(NotFoundExceptionMapper::class.java)
        resourceLoader.register(StubNotFoundExceptionMapper::class.java)
        registerCustomProviders(resourceLoader)
        addJerseyServlet(resourceLoader, context)
        addRequestContextFilter(context)
        context.addServletMapping("/*", "jersey-container-servlet")

        tomcat.start()
        tomcat.server.await()
    }

    private fun getInterfacesToStub(): List<Class<*>> {
        return AutoDiscoveryOfResourceInterfaces(reflections).getResourceInterfaces()
    }

    private fun addJerseyServlet(resourceLoader: ResourceLoaderOfProxyInstances, context: Context) {
        val resourceConfig = ResourceConfig.forApplication(resourceLoader)
        val servletContainer = ServletContainer(resourceConfig)
        Tomcat.addServlet(context, "jersey-container-servlet", servletContainer)
    }

    private fun registerCustomProviders(resourceLoader: ResourceLoaderOfProxyInstances) {
        resourceLoader.packages(* packagesToScan.toTypedArray())
        resourceLoader.registerClasses(classesToRegister)
    }

    private fun instantiateDependencies(): MethodInvocationHandler {
        val stubDefinitions: List<StubsDefinition> = getStubDefinitions()
        ResponseFromStubCreator.useJerseyDeserialization = useJerseyDeserialization
        val methodStubsLoader = CompositeMethodStubLoader(
                KotlinMethodStubsLoader(stubDefinitions),
                YamlMethodStubsLoader())
        return MethodInvocationHandler(methodStubsLoader)
    }

    private fun getStubDefinitions(): List<StubsDefinition> {
        if (autoDiscoveryOfStubDefinitions)
            return stubDefinitions + AutoDiscoveryOfStubDefinitions(reflections).getStubDefinitions()
        else
            return stubDefinitions
    }

    private fun addRequestContextFilter(context: Context) {
        val def = FilterDef().apply {
            filterName = ContextSaveFilter::class.java.simpleName
            filter = ContextSaveFilter()
            context.addFilterDef(this)
        }
        FilterMap().apply {
            filterName = def.filterName
            addURLPattern("/*")
            context.addFilterMap(this)
        }
    }
}