package com.fpinkotlin.common

import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService


sealed class List<out A> {

    abstract fun isEmpty(): Boolean

    abstract fun init(): List<A>

    abstract fun lengthMemoized(): Int

    abstract fun headSafe(): Result<A>

    abstract fun <B> foldLeft(identity: B, zero: B,
                              f: (B) -> (A) -> B): Pair<B, List<A>>

    fun <B> parMap(es: ExecutorService, g: (A) -> B): Result<List<B>> =
            try {
                val result = this.map { x ->
                    es.submit<B> { g(x) }
                }.map<B> { fb ->
                    try {
                        fb.get()
                    } catch (e: InterruptedException) {
                        throw RuntimeException(e)
                    } catch (e: ExecutionException) {
                        throw RuntimeException(e)
                    }
                }
                Result(result)
            } catch (e: Exception) {
                Result.failure(e)
            }

    fun <B> parFoldLeft(es: ExecutorService,
                        identity: B,
                        f: (B) -> (A) -> B,
                        m: (B) -> (B) -> B): Result<B> =
            try {
                val result: List<B> = divide(1024).map { list: List<A> ->
                    es.submit<B> { list.foldLeft(identity, f) }
                }.map<B> { fb ->
                    try {
                        fb.get()
                    } catch (e: InterruptedException) {
                        throw RuntimeException(e)
                    } catch (e: ExecutionException) {
                        throw RuntimeException(e)
                    }
                }
                Result(result.foldLeft(identity, m))
            } catch (e: Exception) {
                Result.failure(e)
            }

    fun splitListAt(index: Int): List<List<A>> {
        tailrec fun splitListAt(acc: List<A>,
                                list: List<A>, i: Int): List<List<A>> =
                when (list) {
                    is Nil -> List(list.reverse(), acc)
                    is Cons ->  if (i == 0)
                        List(list.reverse(), acc)
                    else
                        splitListAt(acc.cons(list.head), list.tail, i - 1)
                }
        return when {
            index < 0        -> splitListAt(0)
            index > length() -> splitListAt(length())
            else             -> splitListAt(Nil, this.reverse(), this.length() - index)
        }
    }

    fun divide(depth: Int): List<List<A>> {
        tailrec
        fun divide(list: List<List<A>>, depth: Int): List<List<A>> =
                when (list) {
                    is Nil -> list // dead code
                    is Cons ->
                        if (list.head.length() < depth || depth < 2)
                            list
                        else
                            divide(list.flatMap { x -> x.splitListAt(x.length() / 2) }, depth / 2)
                }
        return if (this.isEmpty())
            List(this)
        else
            divide(List(this), depth)
    }

    fun exists(p: (A) -> Boolean): Boolean =
            foldLeft(false, true) { x -> { y: A -> x || p(y) } }.first

    fun forAll(p: (A) -> Boolean): Boolean = !exists { !p(it) }

    fun <B> groupBy(f: (A) -> B): Map<B, List<A>> =
            foldLeft(mapOf()) { mt: Map<B, List<A>> ->
                { t ->
                    val k = f(t)
                    mt + (k to (mt.getOrDefault(k, Nil)).cons(t))
                }
            }

    fun splitAt(index: Int): Pair<List<A>, List<A>> {
        tailrec fun splitAt(acc: List<A>,
                            list: List<A>, i: Int): Pair<List<A>, List<A>> =
                when (list) {
                    is Nil -> Pair(list.reverse(), acc)
                    is Cons ->  if (i == 0)
                        Pair(list.reverse(), acc)
                    else
                        splitAt(acc.cons(list.head), list.tail, i - 1)
                }
        return when {
            index < 0        -> splitAt(0)
            index > length() -> splitAt(length())
            else             -> splitAt(Nil, this.reverse(), this.length() - index)
        }
    }

    fun getAt(index: Int): Result<A> {
        data class Pair<out A>(val first: Result<A>, val second: Int) {
            override fun equals(other: Any?): Boolean {
                return when {
                    other == null -> false
                    other.javaClass == this.javaClass -> (other as Pair<A>).second == second
                    else -> false
                }
            }
        }

        return Pair<A>(Result.failure("Index out of bound"), index).let { identity ->
            Pair<A>(Result.failure("Index out of bound"), -1).let { zero ->
                if (index < 0 || index >= length())
                    identity
                else
                    foldLeft(identity, zero) { ta: Pair<A> ->
                        { a: A ->
                            if (ta.second < 0)
                                ta
                            else
                                Pair(Result(a), ta.second - 1)
                        }
                    }.first
            }
        }.first
    }

    fun <A1, A2> unzip(f: (A) -> Pair<A1, A2>): Pair<List<A1>, List<A2>> =
            this.coFoldRight(Pair(Nil, Nil)) { a ->
                { listPair: Pair<List<A1>, List<A2>> ->
                    f(a).let {
                        Pair(listPair.first.cons(it.first), listPair.second.cons(it.second))
                    }
                }
            }

    fun lastSafe(): Result<A> = foldLeft(
            Result()) { _: Result<A> -> { y: A ->
        Result(y)
    } }

    fun drop(n: Int): List<A> = drop(this, n)

    fun dropWhile(p: (A) -> Boolean): List<A> = dropWhile(this, p)

    fun reverse(): List<A> = foldLeft(Nil as List<A>, { acc -> { acc.cons(it) } })

    fun <B> foldRight(identity: B, f: (A) -> (B) -> B): B = foldRight(this, identity, f)

    fun <B> foldLeft(identity: B, f: (B) -> (A) -> B): B = foldLeft(identity, this, f)

    fun length(): Int = foldLeft(0) { { _ -> it + 1} }

    fun <B> foldRightViaFoldLeft(identity: B, f: (A) -> (B) -> B): B =
            this.reverse().foldLeft(identity) { x -> { y -> f(y)(x) } }

    fun <B> coFoldRight(identity: B, f: (A) -> (B) -> B): B = coFoldRight(identity, this.reverse(), identity, f)

    fun <B> map(f: (A) -> B): List<B> = foldLeft(Nil) { acc: List<B> -> { h: A -> Cons(f(h), acc) } }.reverse()

    fun <B> flatMap(f: (A) -> List<B>): List<B> = coFoldRight(Nil) { h -> { t: List<B> -> f(h).concat(t) } }

    fun filter(p: (A) -> Boolean): List<A> = flatMap { a -> if (p(a)) List(a) else Nil }

    internal object Nil: List<Nothing>() {

        override fun <B> foldLeft(identity: B, zero: B, f: (B) -> (Nothing) -> B):
                Pair<B, List<Nothing>> = Pair(identity, Nil)

        override fun headSafe(): Result<Nothing> = Result()

        override fun lengthMemoized(): Int = 0

        override fun init(): List<Nothing> = throw IllegalStateException("init called on an empty list")

        override fun isEmpty() = true

        override fun toString(): String = "[NIL]"

        override fun equals(other: Any?): Boolean = other is Nil

        override fun hashCode(): Int = 0
    }

    internal class Cons<out A>(internal val head: A,
                               internal val tail: List<A>): List<A>() {

        override fun <B> foldLeft(identity: B, zero: B, f: (B) -> (A) -> B): Pair<B, List<A>> {
            fun <B> foldLeft(acc: B, zero: B, list: List<A>, f: (B) -> (A) -> B): Pair<B, List<A>> = when (list) {
                is Nil -> Pair(acc, list)
                is Cons -> if (acc == zero)
                    Pair(acc, list)
                else
                    foldLeft(f(acc)(list.head), zero, list.tail, f)
            }
            return foldLeft(identity, zero, this, f)
        }

        override fun headSafe(): Result<A> = Result(
                head)

        private val length: Int = tail.lengthMemoized() + 1

        override fun lengthMemoized() = length

        override fun init(): List<A> = reverse().drop(1).reverse()

        override fun isEmpty() = false

        override fun toString(): String = "[${toString("", this)}NIL]"

        tailrec private fun toString(acc: String, list: List<A>): String = when (list) {
            is Nil  -> acc
            is Cons -> toString("$acc${list.head}, ", list.tail)
        }
    }

    companion object {

        fun <A> cons(a: A, list: List<A>): List<A> = Cons(a, list)

        tailrec fun <A> drop(list: List<A>, n: Int): List<A> = when (list) {
            is Nil -> list
            is Cons -> if (n <= 0) list else drop(list.tail, n - 1)
        }

        tailrec fun <A> dropWhile(list: List<A>, p: (A) -> Boolean): List<A> = when (list) {
            is Nil -> list
            is Cons -> if (p(list.head)) dropWhile(list.tail, p) else list
        }

        fun <A> concat(list1: List<A>, list2: List<A>): List<A> = list1.reverse().foldLeft(list2) { x -> x::cons }

        fun <A> concat_(list1: List<A>, list2: List<A>): List<A> = foldRight(list1, list2) { x -> { y -> Cons(x, y) } }

        fun <A, B> foldRight(list: List<A>, identity: B, f: (A) -> (B) -> B): B =
                when (list) {
                    is List.Nil -> identity
                    is List.Cons -> f(list.head)(foldRight(list.tail, identity, f))
                }

        tailrec fun <A, B> foldLeft(acc: B, list: List<A>, f: (B) -> (A) -> B): B =
                when (list) {
                    is List.Nil -> acc
                    is List.Cons -> foldLeft(f(acc)(list.head), list.tail, f)
                }

        tailrec fun <A, B> coFoldRight(acc: B, list: List<A>, identity: B, f: (A) -> (B) -> B): B =
                when (list) {
                    is List.Nil -> acc
                    is List.Cons -> coFoldRight(f(list.head)(acc), list.tail, identity, f)
                }


        operator fun <A> invoke(vararg az: A): List<A> =
                az.foldRight(Nil, { a: A, list: List<A> -> Cons(a, list) })
    }
}

fun <A> flatten(list: List<List<A>>): List<A> = list.coFoldRight(List.Nil) { x -> x::concat }

fun <A> List<A>.setHead(a: A): List<A> = when (this) {
    is List.Cons -> List.Cons(a, this.tail)
    is List.Nil -> throw IllegalStateException("setHead called on an empty list")
}

fun <A> List<A>.cons(a: A): List<A> = List.Cons(a, this)

fun <A> List<A>.concat(list: List<A>): List<A> = List.Companion.concat(this, list)

fun <A> List<A>.concat_(list: List<A>): List<A> = List.Companion.concat_(this, list)

fun sum(list: List<Int>): Int = list.foldRight(0, { x -> { y -> x + y } })

fun product(list: List<Double>): Double = list.foldRight(1.0, { x -> { y -> x * y } })

fun triple(list: List<Int>): List<Int> =
        List.foldRight(list, List()) { h -> { t: List<Int> -> t.cons(h * 3) } }

fun doubleToString(list: List<Double>): List<String> =
        List.foldRight(list, List())  { h -> { t: List<String> -> t.cons(h.toString()) } }

tailrec fun <A> lastSafe(list: List<A>): Result<A> = when (list) {
    is List.Nil  -> Result()
    is List.Cons<A> -> when (list.tail) {
        is List.Nil  -> Result(list.head)
        is List.Cons -> lastSafe(list.tail)
    }
}

fun <A> flattenResult(list: List<Result<A>>): List<A> =
        flatten(list.foldRight(List()) { ra: Result<A> ->
            { lla: List<List<A>> -> lla.cons(ra.map { List(it)}.getOrElse(List())) }
        })

fun <A> flattenResultLeft(list: List<Result<A>>): List<A> =
        flatten(list.foldLeft(List()) { lla: List<List<A>> ->
            { ra: Result<A> ->
                lla.cons(ra.map { List(it)}.getOrElse(List()))
            }
        }).reverse()

fun <A> sequenceLeft(list: List<Result<A>>): Result<List<A>> =
        list.foldLeft(Result(
                List())) { x: Result<List<A>> ->
            { y -> map2(y, x) { a -> { b: List<A> -> b.cons(a) } } }
        }.map { it.reverse() }

fun <A> sequence2(list: List<Result<A>>): Result<List<A>> =
        list.filter{ !it.isEmpty() }.foldRight(Result(List())) { x ->
            { y: Result<List<A>> ->
                map2(x, y) { a -> { b: List<A> -> b.cons(a) } }
            }
        }

fun <A, B> traverse(list: List<A>, f: (A) -> Result<B>): Result<List<B>> =
        list.foldRight(Result(List())) { x ->
            { y: Result<List<B>> ->
                map2(f(x), y) { a -> { b: List<B> -> b.cons(a) } }
            }
        }

fun <A> sequence(list: List<Result<A>>): Result<List<A>> =
        traverse(list, { x: Result<A> -> x })

fun <A, B, C> zipWith(list1: List<A>,
                      list2: List<B>,
                      f: (A) -> (B) -> C): List<C> {
    tailrec
    fun <A, B, C> zipWith(acc: List<C>,
                          list1: List<A>,
                          list2: List<B>,
                          f: (A) -> (B) -> C): List<C> = when (list1) {
        is List.Nil -> acc
        is List.Cons -> when (list2) {
            is List.Nil -> acc
            is List.Cons ->
                zipWith(acc.cons(f(list1.head)(list2.head)),
                        list1.tail, list2.tail, f)
        }
    }
    return zipWith(List(), list1, list2, f).reverse()
}

fun <A, B, C> product(list1: List<A>,
                      list2: List<B>,
                      f: (A) -> (B) -> C): List<C> =
        list1.flatMap { a -> list2.map { b -> f(a)(b) } }

fun <A, B> unzip(list: List<Pair<A, B>>): Pair<List<A>, List<B>> = list.unzip { it }

fun <A> List<A>.startsWith(sub: List<A>): Boolean {
    tailrec fun startsWith(list: List<A>, sub: List<A>): Boolean =
            when (sub) {
                is List.Nil  -> true
                is List.Cons -> when (list) {
                    is List.Nil  -> false
                    is List.Cons -> if (list.head == sub.head)
                        startsWith(list.tail, sub.tail)
                    else
                        false
                }
            }
    return startsWith(this, sub)
}

fun <A> List<A>.hasSubList(sub: List<A>): Boolean {
    tailrec
    fun <A> hasSubList(list: List<A>, sub: List<A>): Boolean =
            when (list) {
                is List.Nil -> sub.isEmpty()
                is List.Cons ->
                    if (list.startsWith(sub))
                        true
                    else
                        hasSubList(list.tail, sub)
            }
    return hasSubList(this, sub)
}

fun <A, S> unfoldResult(z: S, getNext: (S) -> Result<Pair<A, S>>): Result<List<A>> {
    tailrec fun unfold(acc: List<A>, z: S): Result<List<A>> {
        val next = getNext(z)
        return when (next) {
            is Result.Empty -> Result(acc)
            is Result.Failure -> Result.failure(next.exception)
            is Result.Success ->
                unfold(acc.cons(next.value.first), next.value.second)
        }
    }
    return unfold(List.Nil, z).map(List<A>::reverse)
}

fun <A, S> unfold(z: S, getNext: (S) -> Option<Pair<A, S>>): List<A> {
    tailrec fun unfold(acc: List<A>, z: S): List<A> {
        val next = getNext(z)
        return when (next) {
            is Option.None -> acc
            is Option.Some ->
                unfold(acc.cons(next.value.first), next.value.second)
        }
    }
    return unfold(List.Nil, z).reverse()
}

fun range(start: Int, end: Int): List<Int> =
        unfold(start) { i ->
            if (i < end)
                Option(Pair(i, i + 1))
            else
                Option()
        }

fun List<Double>.sum() = this.foldRight(0.0, { x -> { y -> x + y } })
