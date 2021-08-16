package com.sample.protifydroid

import com.conversantmedia.util.concurrent.ConcurrentStack
import java.util.*

fun List<Client>.contains(uuid: UUID): Boolean {
    return this.find { it.uuid == uuid } != null
}
fun ConcurrentStack<String>.popEach(body: (String)->Unit) {
    while (size() > 0) {
        body(pop())
    }
}


