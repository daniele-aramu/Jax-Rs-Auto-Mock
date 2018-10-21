package ua.kurinnyi.jaxrs.auto.mock

import org.apache.commons.io.IOUtils
import org.glassfish.jersey.internal.util.collection.ImmutableMultivaluedMap
import org.glassfish.jersey.message.MessageBodyWorkers
import org.glassfish.jersey.server.ContainerRequest
import java.lang.reflect.Method
import javax.ws.rs.container.ContainerRequestContext
import javax.ws.rs.container.ContainerRequestFilter
import javax.ws.rs.core.MediaType

class JerseyInternalsFilter : ContainerRequestFilter {

    companion object {
        private lateinit var workers: MessageBodyWorkers

        fun <T> prepareResponse(method: Method, body: String): T {
            val type = method.returnType as Class<T>
            val genericType = method.genericReturnType
            val inputStream = IOUtils.toInputStream(body)
            val bodyReader = workers.getMessageBodyReader(type, type, emptyArray(), MediaType.APPLICATION_JSON_TYPE, null)
            return bodyReader.readFrom(type, genericType, emptyArray(), MediaType.APPLICATION_JSON_TYPE,
                    ImmutableMultivaluedMap.empty(), inputStream)
        }
    }

    override fun filter(requestContext: ContainerRequestContext) {
        workers = (requestContext as ContainerRequest).workers
    }

}
