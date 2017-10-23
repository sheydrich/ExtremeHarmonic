# ExtremeHarmonic

This page contains information and code related to the paper [Beating the Harmonic lower bound for online bin packing](https://arxiv.org/abs/1511.00876) by [Sandy Heydrich](http://people.mpi-inf.mpg.de/~heydrich/) and [Rob van Stee](http://www.cs.le.ac.uk/people/rvs4/). 

### ExtremeHarmonicVerifier program

We provide our executable program as a `.jar`-file, we require that [Java8](http://www.oracle.com/technetwork/java/javase/downloads/jre8-downloads-2133155.html) is installed on your machine. This is a command-line based tool and lacks graphical user interface. To execute our program, open your command-line, navigate to the directory containing the `.jar`-file and execute `java -jar ExtremeHarmonicVerifier.jar` to start the program.

During its executing, the program will search for a `.vp`-file inside its directory. If it finds the file we used to achieve our results with (provided below), it will use this file as an input. Otherwise, it will query you for an input file. This file must contain all parameters required by the program, described in detail below. Alternatively, you can supply the name of a `.vp`-file you want to use as input as command line argument (e.g., execute `java -jar ExtremeHarmonicVerifier.jar 1.583.vp` using the simpler 1.583-input provided below).

The program generates a file `protocol_ExtremeHarmonic.txt` with detailed information on the execution, a file `params.txt` with detailed information about the non-large types used, a file `knapsackData.txt` containing the information needed for verifying the solutions of the knapsack problems with an external knapsack solver and a file `weights.txt` that contains item sizes and weights for all cases in human-readable form (though the values are rounded and thus not exact).

