package com.github.spigotbasics.core.command.parsed

import com.github.spigotbasics.common.Either
import com.github.spigotbasics.core.Basics
import com.github.spigotbasics.core.command.parsed.arguments.CommandArgument
import com.github.spigotbasics.core.command.parsed.context.CommandContext
import com.github.spigotbasics.core.extensions.lastOrEmpty
import com.github.spigotbasics.core.logger.BasicsLoggerFactory
import com.github.spigotbasics.core.messages.Message
import org.bukkit.command.CommandSender
import org.bukkit.permissions.Permission
import kotlin.math.min

class ArgumentPath<T : CommandContext>(
    val senderArgument: SenderType<*>,
    val arguments: List<Pair<String, CommandArgument<*>>>,
    val permission: List<Permission> = emptyList(),
    private val contextBuilder: (Map<String, Any?>) -> T,
    val ownExecutor: CommandContextExecutor<T>? = null,
) {
    companion object {
        val logger = BasicsLoggerFactory.getCoreLogger(ArgumentPath::class)
    }

    init {
        require(arguments.none { it.first == "sender" }) { "Argument name 'sender' is reserved" }
    }

    fun matches(
        sender: CommandSender,
        args: List<String>,
    ): Either<PathMatchResult, Pair<Double, List<Message>>> {
        val greedyArg = arguments.indexOfFirst { it.second.greedy }
        var firstErrorIndex = -1.0

        // Each provided arg must be parseable by its corresponding CommandArgument
        val errors = mutableListOf<Message>()
        arguments.indices.forEach { index ->

            if (index >= args.size) {
                return@forEach // Needed because we now iterate over arguments and not args anymore
            }

            // val parsed = arguments[index].parse(args[index])
            val (_, argument) = arguments[index]

            val currentArgResult = accumulateArguments(index, args, arguments.map { it.second }, greedyArg)

            if (currentArgResult is Either.Right) {
                errors.add(Basics.messages.notEnoughArgumentsGivenForArgument(argument.name))
                val missingArguments = currentArgResult.value
                // "First error" is the first missing argument, adjusted by 0.5 so that arguments with matching paths are preferred
                val returnValue = (index + 1) - missingArguments + 0.5
                firstErrorIndex = if (firstErrorIndex == -1.0) returnValue else min(firstErrorIndex, returnValue) // TODO - is this correct?
                return@forEach
            }

            val currentArg = currentArgResult.leftOrNull()!!

            val parsed = argument.parse(sender, currentArg)

            if (parsed == null) {
                val error = argument.errorMessage(sender, currentArg)
                firstErrorIndex = if (firstErrorIndex == -1.0) index.toDouble() else min(firstErrorIndex, index.toDouble())
                errors.add(error)
            }
        }

        if (errors.isNotEmpty()) return Either.Right(firstErrorIndex to errors)

        if (greedyArg == -1) {
            if (args.size > arguments.sumOf { it.second.length }) {
                return Either.Right(
                    arguments.size.toDouble() to listOf(Basics.messages.tooManyArguments),
                )
            }
        }

        if (!senderArgument.requiredType.isInstance(sender)) return Either.Left(PathMatchResult.YES_BUT_NOT_FROM_CONSOLE)
        if (!hasPermission(sender)) return Either.Left(PathMatchResult.YES_BUT_NO_PERMISSION)
        return Either.Left(PathMatchResult.YES)
    }

    fun accumulateArguments(
        argIndex: Int,
        givenArgs: List<String>,
        commandArguments: List<CommandArgument<*>>,
        greedyPosition: Int,
    ): Either<String, Int> {
        val result = accumulateArguments0(argIndex, givenArgs, commandArguments, greedyPosition)
        logger.debug(400, "Accumulated arguments @ $argIndex  ----- $result")
        return result
    }

    fun accumulateArguments0(
        argIndex: Int,
        givenArgs: List<String>,
        commandArguments: List<CommandArgument<*>>,
        greedyPosition: Int,
    ): Either<String, Int> {
        // Count length of combined arguments before this one
        var myStartIndex = commandArguments.subList(0, argIndex).sumOf { it.length }
        val mySupposedLength = commandArguments[argIndex].length
        val myEndIndex = myStartIndex + mySupposedLength
        var missingArguments = mySupposedLength - (givenArgs.size - myStartIndex)

        if (myEndIndex > givenArgs.size) {
            return Either.Right(missingArguments) // TODO: Is this correct?
        }

        val expectedLengthWithoutGreedy = commandArguments.filter { !it.greedy }.sumOf { it.length }

        if (greedyPosition == -1 || argIndex < greedyPosition) {
            return Either.Left(givenArgs.subList(myStartIndex, myEndIndex).joinToString(" "))
        }

        val greedyArgumentSize = givenArgs.size - expectedLengthWithoutGreedy
        val extraArgs = givenArgs.subList(greedyPosition, greedyPosition + greedyArgumentSize)

        logger.debug(
            600,
            "Accumulating arguments: argIndex: $argIndex, givenArgs: $givenArgs, commandArguments: $commandArguments, " +
                "greedyPosition: $greedyPosition",
        )
        logger.debug(500, "GreedyArgumentSize: $greedyArgumentSize, extraArgs: $extraArgs")

        if (argIndex == greedyPosition) {
            return Either.Left(extraArgs.joinToString(" "))
        }

        val lengthAfterMe = commandArguments.subList(argIndex + 1, commandArguments.size).sumOf { it.length }
        myStartIndex = givenArgs.size - lengthAfterMe - 1

        missingArguments = mySupposedLength - (givenArgs.size - myStartIndex)

        if (myStartIndex + 1 > givenArgs.size) {
            return Either.Right(missingArguments)
        }

        return Either.Left(givenArgs.subList(myStartIndex, givenArgs.size).joinToString(" "))
    }

    fun parse(
        sender: CommandSender,
        args: List<String>,
    ): ParseResult<T> {
        logger.debug(
            10,
            "ArgumentPath#parse: sender: $sender, args: $args, senderArgument: $senderArgument, arguments: $arguments, " +
                "permission: $permission, contextBuilder: $contextBuilder",
        )

        val greedyArg = arguments.indexOfFirst { it.second.greedy } // TODO: Can be field

        if (!senderArgument.requiredType.isInstance(sender)) {
            logger.debug(10, "Failure: senderArgument.requiredType.isInstance(sender) is false")
            return ParseResult.Failure(listOf(Basics.messages.commandNotFromConsole))
        }

        val parsedArgs = mutableMapOf<String, Any?>("sender" to sender)
        val errors = mutableListOf<Message>()

        for ((index, argumentPair) in arguments.withIndex()) {
            val (argName, arg) = argumentPair
            if (index >= args.size) {
                logger.debug(10, "Failure: index >= args.size")
                errors.add(Basics.messages.missingArgument(arg.name))
                break
            }

            val currentArgResult = accumulateArguments(index, args, arguments.map { it.second }, greedyArg)

            if (currentArgResult is Either.Right) {
                errors.add(Basics.messages.notEnoughArgumentsGivenForArgument(arg.name))
                break
            }

            val currentArg = currentArgResult.leftOrNull()!!

            val parsed = arg.parse(sender, currentArg)
            if (parsed == null) {
                logger.debug(10, "Failure: parsed == null for arg: $arg, args[$index]: ${args[index]} (index: $index)")
                errors.add(arg.errorMessage(sender, currentArg))
                break
            } else {
                logger.debug(10, "  parsed: $parsed for arg: $arg, args[$index]: ${args[index]} (index: $index)")
                parsedArgs[argName] = parsed
            }
        }

        return if (errors.isEmpty() &&
            parsedArgs.size == arguments.size + 1 // One extra for the sender
        ) {
            logger.debug(10, "Success: errors.isEmpty() && parsedArgs.size == arguments.size + 1")
            ParseResult.Success(contextBuilder(parsedArgs))
        } else {
            logger.debug(10, "Failure: errors.isNotEmpty() || parsedArgs.size != arguments.size")
            logger.debug(10, "Errors empty: ${errors.isEmpty()}")
            logger.debug(10, "Parsed args size: ${parsedArgs.size}, arguments size: ${arguments.size}")
            if (errors.isEmpty()) {
                // errors.add(coreMessages.commandArgumentSizeMismatch)
                throw IllegalStateException("parsedArgs.size != arguments.size +1, but errors.isEmpty()")
            }
            ParseResult.Failure(errors)
        }
    }

    /**
     * Checks if this path matches the input until the end of the input. This is only used for tabcompletes.
     *
     * @param sender
     * @param input
     * @return
     */
    fun matchesStart(
        sender: CommandSender,
        input: List<String>,
    ): Boolean {
        logger.debug(200, "TabComplete matchesStart: input: $input @ $this")
        if (input.size > arguments.sumOf { it.second.length }) {
            logger.debug(200, "  input.size > arguments.size")
            return false
        }
        input.forEachIndexed { index, s ->
            logger.debug(200, "  Checking index $index, s: $s")
            if (index == input.size - 1) {
                logger.debug(200, "    Last argument, skipping")
                return@forEachIndexed
            } // Last argument is still typing
            val arg = arguments[index].second
            val parsed = arg.parse(sender, s)
            if (parsed == null) {
                logger.debug(200, "     parsed == null")
                return false
            }
        }
        logger.debug(200, "  All arguments parsed, this path matches the input")
        return true
    }

    fun tabComplete(
        sender: CommandSender,
        args: List<String>,
    ): List<String> {
        if (args.isEmpty() || args.size > arguments.size) return emptyList()

        val currentArgIndex = args.size - 1
        return arguments[currentArgIndex].second.tabComplete(sender, args.lastOrEmpty())
    }

    fun isCorrectSender(sender: CommandSender): Boolean {
        return senderArgument.requiredType.isInstance(sender)
    }

    fun hasPermission(sender: CommandSender): Boolean {
        return permission.all { sender.hasPermission(it) }
    }

    override fun toString(): String {
        return "ArgumentPath(senderArgument=$senderArgument, arguments=$arguments, permission=$permission)"
    }
}
