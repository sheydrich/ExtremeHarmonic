# ExtremeHarmonic

This page contains information and code related to the paper [Beating the Harmonic lower bound for online bin packing](https://arxiv.org/abs/1511.00876) by [Sandy Heydrich](http://people.mpi-inf.mpg.de/~heydrich/) and [Rob van Stee](http://www.cs.le.ac.uk/people/rvs4/). 

## ExtremeHarmonicVerifier program

We provide our executable program as a `.jar`-file, we require that [Java8](http://www.oracle.com/technetwork/java/javase/downloads/jre8-downloads-2133155.html) is installed on your machine. This is a command-line based tool and lacks graphical user interface. To execute our program, open your command-line, navigate to the directory containing the `.jar`-file and execute `java -jar ExtremeHarmonicVerifier.jar` to start the program.

During its executing, the program will search for a `.vp`-file inside its directory. If it finds the file we used to achieve our results with (provided below), it will use this file as an input. Otherwise, it will query you for an input file. This file must contain all parameters required by the program, described in detail below. Alternatively, you can supply the name of a `.vp`-file you want to use as input as command line argument (e.g., execute `java -jar ExtremeHarmonicVerifier.jar 1.583.vp` using the simpler 1.583-input provided below).

The program generates a file `protocol_ExtremeHarmonic.txt` with detailed information on the execution, a file `params.txt` with detailed information about the non-large types used, a file `knapsackData.txt` containing the information needed for verifying the solutions of the knapsack problems with an external knapsack solver and a file `weights.txt` that contains item sizes and weights for all cases in human-readable form (though the values are rounded and thus not exact).
* [ExtremeHarmonicVerifier.jar](https://github.com/sheydrich/ExtremeHarmonic/blob/master/executables/ExtremeHarmonicVerifier.jar)

We provide two input files for this program, one to prove the competitive ratio 1.5813 for Son of Harmonic and one to prove the competitive ratio of 1.583 within the Extreme Harmonic framework:
* [1.5813.vp](https://github.com/sheydrich/ExtremeHarmonic/blob/master/executables/1.5813.vp) 
* [1.583.vp](https://github.com/sheydrich/ExtremeHarmonic/blob/master/executables/1.583.vp) 

Using `1.5813.vp`, the program should run not more than a few minutes on a modern machine. Using `1.583.vp`, only 23 knapsack problems are generated, so that it is relatively easy to verify this result (which already proves that we can get below the Super Harmonic-lower bound). The program also runs much faster and should complete in a few seconds.

### BinarySearch program

This program was used to determine the y3-values that certify the feasibility of the dual LPs for Son of Harmonic. It can be executed the same way as `ExtremeHarmonicVerifier.jar` and uses a `.bsp`-file as input. This program generates the verifier input file as well. We again provide two input files for this program, for competitive ratios 1.5813 and 1.583.
* [BinarySearch.jar](https://github.com/sheydrich/ExtremeHarmonic/blob/master/executables/BinarySearch.jar)
* [1.5813.bsp](https://github.com/sheydrich/ExtremeHarmonic/blob/master/executables/1.5813.bsp)
* [1.583.bsp](https://github.com/sheydrich/ExtremeHarmonic/blob/master/executables/1.583.bsp)

### ParameterOptimizer program

This program was used to determine most of the parameters of the Son of Harmonic algorithm. Given only few manually set parameters, it adds additional types and computes red-values for them using heuristics that are supposed to make sure that the final competitive ratio (which is already supplied to this program) can be achieved. It can be executed the same way as `ExtremeHarmonicVerifier.jar` and uses a `.pop`-file as input. This program generates the input file for the `BinarySearch.jar` program as well. We again provide two input files for this program, for competitive ratios 1.5813 and 1.583.
* [ParameterOptimizer.jar](https://github.com/sheydrich/ExtremeHarmonic/blob/master/executables/ParameterOptimizer.jar)
* [1.5813.pop](https://github.com/sheydrich/ExtremeHarmonic/blob/master/executables/1.5813.pop)
* [1.583.pop](https://github.com/sheydrich/ExtremeHarmonic/blob/master/executables/1.583.pop)

## SuperHarmonicVerifier program

In addition, we provide a simplified version of our program, `SuperHarmonicVerifier.jar`, that can be used to verify the competitive ratio of Harmonic++ or other Super Harmonic algorithms as given by Seiden (S.S. Seiden "On the Online Bin Packing Problem", J. ACM 49(5), 640-671 (2002)). It works very similar to the `ExtremeHarmonicVerifier.jar` described above, however, you can additionally supply a command line argument to specify whether to prove the results with the exact parameters Seiden used (command line argument `original`) or whether to prove that a 1.5884-competitive algorithm exists within the SuperHarmonic framework by using improved (and also simplified) parameters provided by us (command line argument `improved`). If no argument is specified, the improved version is run.

* [SuperHarmonicVerifier.jar](https://github.com/sheydrich/ExtremeHarmonic/blob/master/executables/SuperHarmonicVerifier.jar)
* [1.5888.vp](https://github.com/sheydrich/ExtremeHarmonic/blob/master/executables/1.5888.vp)
* [1.5884.vp](https://github.com/sheydrich/ExtremeHarmonic/blob/master/executables/1.5884.vp)

* [BinarySearchSH.jar](https://github.com/sheydrich/ExtremeHarmonic/blob/master/executables/BinarySearchSH.jar)
* [1.5888.bsp](https://github.com/sheydrich/ExtremeHarmonic/blob/master/executables/1.5888.bsp)
* [1.5884.bsp](https://github.com/sheydrich/ExtremeHarmonic/blob/master/executables/1.5884.bsp)

* [ParameterOptimizer.jar](https://github.com/sheydrich/ExtremeHarmonic/blob/master/executables/ParameterOptimizerSH.jar)
* [1.5888.pop](https://github.com/sheydrich/ExtremeHarmonic/blob/master/executables/1.5888.pop)
* [1.5884.pop](https://github.com/sheydrich/ExtremeHarmonic/blob/master/executables/1.5884.pop)

## Other resources

* [knapsackData.txt](https://github.com/sheydrich/ExtremeHarmonic/blob/master/knapsackData.txt): This file contains the input data for the final knapsack problems. This file is also produced as an output of `ExtremeHarmonicVerifier.jar.` By providing this input, we enable readers to check the solution to the knapsack problem with a knapsack solver of their choice.
* [weights.txt](https://github.com/sheydrich/ExtremeHarmonic/blob/master/weights.txt): This file contains the input data for the final knapsack problems, however, in human-readable rounded form (i.e., no exact values are given). This file is also produced as an output of `ExtremeHarmonicVerifier.jar`. This is to enable a reader easy inspection of the weights generated by the program.
