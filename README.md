[![Build Status](https://travis-ci.org/ruediste/lambda-peg-parser.svg?branch=master)](https://travis-ci.org/ruediste/lambda-peg-parser)

# Lambda Parsing Expression Grammar Parser
This is a Java library providing easy-to-use, powerful and elegant parsing of arbitrary text. It is based on Parsing Expression Grammars (PEGs), which are similar to simple recursive descent parsers, as you would write them by hand, with the addition of backtracking (try something, if it fails, try something else) and support for left recursion.

This project is similar to [parboiled](https://github.com/sirthias/parboiled), with the important difference that the rule methods are executed directly and can return any value. This reduces the conceptual difference to hand coded parsers and thus makes learning to use the parser easier.

[![Build Status](https://travis-ci.org/ruediste/lambda-peg-parser.svg?branch=master)](https://travis-ci.org/ruediste/lambda-peg-parser)

## Overview
A grammar is defined by creating a subclass of the **Parser** class. Each method in the subclass defines a parsing rule. While parsing, the methods are basically executed as normal java methods. The input is consumed using utility functions from the base class such as **String** or **Char**. If the expected input is not found, a **NoMatchException** is thrown. Utility classes like **Optional**, **FirstOf** or **OneOrMore** catch **NoMatchException**s and manage backtracking or the using of the already-used result. The rule methods can construct any result, typically an AST, possibly using the results of rule methods they call themselves.

 The following is a parser recognizes expressions like "123+4", "5-2" and "1+2-3-4+5" and evaluates them while parsing:
	
	/**
	 * A simple parser recognizing:
	 * 
	 * <pre>
	 * input = sum EOI
	 * sum = number ('+' number | '-' number)*
	 * number = digit +
	 * </pre>
	 */
	public class PlusMinusParser extends DefaultParser {

		public PlusMinusParser(DefaultParsingContext ctx) {
			super(ctx);
		}

		int input() {
			int result = sum();
			EOI();
			return result;
		}

		int sum() {
			int result = number();
			return ZeroOrMore(() -> FirstOf(() -> {
				String("+");
				return number();
			}, () -> {
				String("-");
				return -number();
			})).stream().reduce(result, (a, b) -> a + b);
		}

		int number() {
			return Integer
					.parseInt(OneOrMoreChars(Character::isDigit, "number"));
		}
	}

As you can see, the rule methods return the value of the term they represent. The **input()** rule uses **EOI()** to make sure the whole input has been processed. **sum()** uses **ZeroOrMore()** to realize the repetition, and **stream().reduce()** on the returned collection to sum up the summands. Finally, **number()** uses **Integer.parseInt()** to parse the numbers.

Instances of the parser are created using the **ParserFactory** and associated with a **ParsingContext**. The context contains the input and the current position of the parser. Any rule method of a parser instance can be called, triggering the parsing process. 

The following code snippet uses the grammar above to parse the input "1+2":

	DefaultParsingContext ctx = new DefaultParsingContext("1+2");
	PlusMinusParser parser = ParserFactory.create(PlusMinusParser.class, ctx);
	int result = parser.input();
	// result is now 3

## Installation
There are no releases or binaries yet. The project is a standard maven project. Clone and build/install.

## Error Reporting
Error reporting uses the farthest failure heuristic discussed in ["Packrat Parsing: a Practical Linear-Time Algorithm with Backtracking" by Bryan Ford](http://bford.info/pub/lang/thesis.pdf) section "3.2.4
Error Handling", which basically reports all unmet expectations which occur at the farthest input position reached by the parser. 

In addition, the **Atomic()** method replaces all expectations of the sub term with a single specified expectation, reported at the beginning of the matching attempt. **Expect()** is the same as **Atomic()**, except that the errors are reported at the position farthest to the right the parser has reached.

## Implementation
Due to the heavy use of lambda expressions, the rule methods implement a recursive descent parser almost as-is. The only missing piece is the support for left recursion. 

An adaption of the algorithm outlined in ["Packrat Parsers Can Support Left Recursion" by Alessandro Warth, James R. Douglass, Todd Millstein](www.vpri.org/pdf/tr2007002_packrat.pdf) is used. We keep track of all rule method invocations on the current stack. Whenever a left-recursive entry into a rule method is detected, the current seed (or failure if no seed exists) is returned. Upon exit of the recursive method, the seed is stored and the rule reevaluated.

To implement this algorithm it is necessary to add an around advice to the rule methods. This could easily be accomplished using a cglib. However, the resulting stack traces contain many artificial entries and debugging experience is far from perfect. Therefore, before instantiating a parser class, the class is transformed using [ASM](http://asm.ow2.org/) to contain the advice in their methods and loaded using a separate class loader. The resulting instance can either be accessed through an interface, or a proxy of the parser class is created which forwards to the real parser instance.

The around advice is contained in **PrototypeParser**. The bytecode is copied form there to each rule method.

## Pluggable Grammars
Plugging different grammars is really easy. Multiple parsers using the same **ParsingContext** can freely cooperate. Just instantiate the parsers using a single context and register them with each other.

## Extensibility
The **ParsingContext** and the **ParsingState** have been prepared to be subclassed. This should allow for a wide range of improvements. 