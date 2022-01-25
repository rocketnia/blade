# Blade

[![CI](https://github.com/rocketnia/blade/actions/workflows/ci.yml/badge.svg)](https://github.com/rocketnia/blade/actions/workflows/ci.yml)

Blade is in very early, skeletal stages, but here's a vague synopsis of the currently intended future:

Blade is a programming language designed for freedom of declaration. A Blade program is a collection of resources which describe general aspects of the program, such that multiple resources can collectively describe a single value. Furthermore, the program itself determines the meaning of these resources.

Besides that, Blade is rather nebulous. In a sense it has to be, since by Blade's very nature a project can include a compiler, code written for that compiler, and other incidental Blade code, all three of which can make statically resolved references to each other as part of the same program.

That said, a lispy core dialect is provided which has support for fexprs, continuations, keyed dynamic variables, and an extensive pure functional subset. This dialect prioritizes being complete, convenient, and adaptable to new paradigms, in that order, in the hope that this approach will promote the development and interoperability of further dialects which emphasize other language qualities such as minimalism, verifiability, and performance.

Like so many other languages, Blade is so many other languages in theory, and it's only so many other languages at the moment.
