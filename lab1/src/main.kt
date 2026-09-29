import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands

private class BenchmarkCli : CliktCommand(name = "benchmarks") {
    override fun run() = Unit
}

fun main(args: Array<String>) = BenchmarkCli()
    .subcommands(RunBenchmarkCommand(), RedrawBenchmarkCommand())
    .main(args)
