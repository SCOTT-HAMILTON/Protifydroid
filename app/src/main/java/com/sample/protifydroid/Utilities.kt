package com.sample.protifydroid

import android.content.Context
import android.os.Message
import android.os.Messenger
import com.conversantmedia.util.concurrent.ConcurrentStack
import java.util.*

val EMPTY_UUID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")

fun UUID.isEmpty(): Boolean {
    return this == EMPTY_UUID
}
fun UUID.isNotEmpty(): Boolean {
    return this != EMPTY_UUID
}
fun List<Client>.contains(uuid: UUID): Boolean {
    return this.find { it.uuid == uuid } != null
}
fun ConcurrentStack<String>.popEach(body: (String)->Unit) {
    while (size() > 0) {
        body(pop())
    }
}
fun ArrayList<Pair<Messenger, BinderChecker>>.add(messenger: Messenger, service: ServerService) {
    val binderChecker = BinderChecker(service as Context)
    messenger.binder.linkToDeath(binderChecker, 0)
    add(messenger to binderChecker)
}

fun Pair<Messenger, BinderChecker>.isValid(): Boolean {
    return first.binder.isBinderAlive &&
            first.binder.pingBinder() &&
            !second.isBinderDead()
}
fun Pair<Messenger, BinderChecker>.send(message: Message): Boolean {
    return if (isValid()) {
        first.send(message)
        true
    } else {
        false
    }
}

