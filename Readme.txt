Ziyi Chen
20517568

Make version: GNU Make 3.81
openjdk version "1.8.0_91"
OpenJDK Runtime Environment (build 1.8.0_91-8u91-b14-0ubuntu4~14.04-b14)
OpenJDK 64-Bit Server VM (build 25.91-b14, mixed mode)

Just use "make" to compile the file.


Example Execution
1. On the hostX: ./nse-linux386 hostY 9999
2. On the router1: java router 1 hostX 9999 9991
2. On the router2: java router 2 hostX 9999 9992
2. On the router3: java router 3 hostX 9999 9993
2. On the router4: java router 4 hostX 9999 9994
2. On the router5: java router 5 hostX 9999 9995

Execution step:
1. Run NSE (eg, ./nse-linux386 ubuntu1404-004.student.cs.uwaterloo.ca 5678 )
2. Run router1-5 on different terminal (eg, 
	java router 1 ubuntu1404-004.student.cs.uwaterloo.ca 5678 2355
	java router 2 ubuntu1404-004.student.cs.uwaterloo.ca 5678 2356 etc..)




test:
1. Tested on ubuntu1404-004.student.cs.uwaterloo.ca