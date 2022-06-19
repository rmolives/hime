# Hime Language
[![Gitter](https://badges.gitter.im/wumoe/hime.svg)](https://gitter.im/wumoe/hime?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge)<br/>
[![License: GPL v3](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](http://www.gnu.org/licenses/gpl-3.0)

## About

This is the interpreter of Hime language, a dialect of Lisp, run on JVM platform.

Once a feature is finished and tested, and not considered harmful, I'll copy the codes here and publish releases.

##  Version

It is still under development and has not released any version!!!

## Examples

### Sqrt

```lisp
(def (sqrt x)
    (def (good-enough guess)
        (< (abs (- (pow guess 2) x)) 0.001))
    (def (improve guess)
        (average guess (/ x guess)))
    (def (sqrt-iter guess)
        (if (good-enough guess)
            guess
            (sqrt-iter (improve guess))))
        (sqrt-iter 1.0))
(println (sqrt 81))
```

### Prime?
```lisp
(def (prime? n)
    (def (divides? a b)
        (= (mod b a) 0))
    (def (find-divisor n test-divisor)
        (cond ((> (sqrt test-divisor) n) n)
            ((divides? test-divisor n) test-divisor)
            (else (find-divisor n (+ test-divisor 1)))))
    (def (smallest-divisor n)
        (find-divisor n 2))
    (= n (smallest-divisor n)))
(while true
    (print "Input Number: ")
    (if (prime? (read-num))
        (println "is prime.")
        (println "not is prime.")))
```

### Fibonacci
```lisp
(def (fib n)
    (cond ((= n 0) 0)
        ((= n 1) 1)
        (else (+ (fib (- n 1)) (fib (- n 2))))))
(println (fib 10))
```
