package me.brightspark.mdcbot.database

import org.jetbrains.exposed.v1.dao.Entity
import org.jetbrains.exposed.v1.dao.EntityClass

fun <ID : Any, E : Entity<ID>, EC : EntityClass<ID, E>> findOrCreate(entity: EC, id: ID, init: (E.() -> Unit) = {}): E =
	entity.findById(id) ?: entity.new(id, init)

fun <ID : Any, E : Entity<ID>, EC : EntityClass<ID, E>> updateOrCreate(entity: EC, id: ID, setter: (E) -> Unit): E =
	entity.findByIdAndUpdate(id) { setter(it) } ?: entity.new(id) { setter(this) }
