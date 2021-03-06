package ua.kurinnyi.jaxrs.auto.mock.kotlin

import org.reflections.Reflections
import ua.kurinnyi.jaxrs.auto.mock.endpoint.GroupResourceImpl

class AutoDiscoveryOfStubDefinitions(private val reflections: Reflections){

    fun getStubDefinitions(): List<StubsDefinition> {
        return reflections.getSubTypesOf(StubsDefinition::class.java)
                .asSequence()
                .filterNot (::isInternalInstance)
                .map {
                    try {
                        it.getConstructor()
                    } catch (e: NoSuchMethodException) {
                        System.err.println("Class ${it.name} has no empty constructor so you should manually add it. " +
                                "As it would not work with auto discovery")
                        null
                    }
                }.filterNotNull()
                .map { it.newInstance() }
                .toList()
    }

    private fun isInternalInstance(it: Class<out StubsDefinition>) =
            it.name == GroupResourceImpl::class.java.name
}
