package com.jstore.com.jstore.order.acl

import org.springframework.beans.factory.annotation.Value
//import org.springframework.cloud.client.loadbalancer.LoadBalanced
//import org.springframework.cloud.context.config.annotation.RefreshScope
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.support.WebClientAdapter
import org.springframework.web.service.annotation.GetExchange
import org.springframework.web.service.annotation.HttpExchange
import org.springframework.web.service.invoker.HttpServiceProxyFactory

@HttpExchange("http://j-store-goods-boot/test")
interface Goods {
    @GetExchange("/get")
    fun get(): ResponseEntity<String>
}

@Configuration
class ResetTemplateConfig {

//    @LoadBalanced
    @Bean
    fun loadBalancerWebClientBuilder(): WebClient.Builder {
        return WebClient.builder().codecs { configurer ->
            configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024)
        }
    }


    @Bean
    fun httpServiceProxyFactory(webClientBuilder: WebClient.Builder): HttpServiceProxyFactory {
        val webClient = webClientBuilder.build()
        return HttpServiceProxyFactory.builderFor(WebClientAdapter.create(webClient)).build()
    }
}

//@RefreshScope
@RestController
@RequestMapping("/test")
class TestController(
    httpServiceProxyFactory: HttpServiceProxyFactory,
) {
    var content: String = ""
        @Value("\${test.content:}")
        set

    private val goods: Goods = httpServiceProxyFactory.createClient(Goods::class.java)

    @GetMapping("get")
    fun get(): ResponseEntity<String> {
        return goods.get()
    }

    @GetMapping("/content")
    fun content(): String {
        return content
    }
}