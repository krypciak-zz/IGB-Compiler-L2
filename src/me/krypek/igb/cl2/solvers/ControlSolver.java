package me.krypek.igb.cl2.solvers;

import static me.krypek.igb.cl1.datatypes.Instruction.Cell_Jump;
import static me.krypek.igb.cl1.datatypes.Instruction.Cell_Return;
import static me.krypek.igb.cl1.datatypes.Instruction.If;
import static me.krypek.igb.cl2.datatypes.BracketType.*;

import java.util.ArrayList;
import java.util.Set;
import java.util.Stack;

import me.krypek.igb.cl1.IGB_MA;
import me.krypek.igb.cl1.datatypes.Instruction;
import me.krypek.igb.cl2.Functions;
import me.krypek.igb.cl2.IGB_CL2_Exception.Err;
import me.krypek.igb.cl2.RAM;
import me.krypek.igb.cl2.datatypes.Bracket;
import me.krypek.igb.cl2.datatypes.BracketType;
import me.krypek.igb.cl2.datatypes.function.Function;
import me.krypek.igb.cl2.datatypes.function.FunctionCall;
import me.krypek.igb.cl2.datatypes.function.FunctionCallField;
import me.krypek.igb.cl2.datatypes.function.UserFunction;
import me.krypek.utils.Utils;

public class ControlSolver {

	public static String[] booleanOperators = { "==", "!=", ">=", "<=", ">", "<", };

	private final Functions functions;
	private final VarSolver varsolver;
	private final EqSolver eqsolver;
	private final RAM ram;
	private final String[] cmd;

	Stack<Bracket> bracketStack;

	private boolean isInElseIfChain = false;

	private int line;

	public ControlSolver(Functions functions, VarSolver varsolver, EqSolver eqsolver, RAM ram, String[] cmd) {
		this.cmd = cmd;
		this.ram = ram;
		this.eqsolver = eqsolver;
		this.functions = functions;
		this.varsolver = varsolver;
		bracketStack = new Stack<>();
		bracketStack.push(Bracket._default());
	}

	private Bracket pop() {
		Bracket bt = bracketStack.pop();
		if(bt.type == Default)
			throw Err.normal("Too many brackets.");
		return bt;
	}

	private void push(Bracket bt) { bracketStack.push(bt); }

	public void checkStack() {
		if(bracketStack.size() != 1)
			throw Err.noLine("Too little brackets.");
	}

	private Bracket getBracket(int depth) {
		assert depth > -1;
		if(depth == 0)
			throw Err.normal("Cannot reach through 0 layers.");
		final int stackSize = bracketStack.size();
		final int index = stackSize - depth;
		if(index <= 0)
			throw Err.normal("Cannot reach that many layers of brackets.");
		return bracketStack.get(index);
	}

	private Bracket lookFor(int amount, Set<BracketType> set) {
		assert amount > 0;
		int found = 0;
		for (int i = bracketStack.size() - 1; i >= 1; i--) {
			Bracket br = bracketStack.get(i);
			if(set.contains(br.type))
				if(++found == amount)
					return br;
		}
		throw Err.normal("Didn't find " + amount + " layers of type: " + set);
	}

	private Bracket nextAdd = Bracket._none();

	public ArrayList<Instruction> cmd(String cmd, int line) {
		this.line = line;
		if(cmd.equals("{")) {
			ram.next();
			push(nextAdd);
			if(nextAdd.type == None)
				nextAdd.accessBracket();
			nextAdd.onBracket.run();

			ArrayList<Instruction> list = Utils.listOf(nextAdd.startPointer);
			list.addAll(nextAdd.startList);
			nextAdd = Bracket._none();
			return list;
		}
		if(cmd.equals("}")) {
			ram.pop();
			Bracket b = pop();
			if(b.type == Function)
				b.endList.add(0, b.endPointer);
			else
				b.endList.add(b.endPointer);

			return b.endList;
		}
		if(cmd.startsWith("break"))
			return _break(cmd.substring(5).stripTrailing());
		if(cmd.startsWith("redo"))
			return _redo(cmd.substring(4).stripTrailing());
		else if(cmd.startsWith("return"))
			return _return(cmd.substring(6).stripTrailing());
		else if(cmd.startsWith("continue"))
			return _continue(cmd.substring(8).stripTrailing());
		else if(cmd.startsWith("if"))
			return _if(cmd.substring(2).stripTrailing());
		else if(cmd.startsWith("while"))
			return _while(cmd.substring(5).stripTrailing());
		else if(cmd.startsWith("for"))
			return _for(cmd.substring(3).stripTrailing());
		else if(cmd.startsWith("else"))
			return _else(cmd.substring(4).stripTrailing());
		else if(cmd.startsWith("raw"))
			return _raw(cmd.substring(3).stripTrailing());
		else {
			int spaceIndex = cmd.indexOf(' ');
			if(spaceIndex != -1) {
				String first = cmd.substring(0, spaceIndex).strip();
				if(first.equals("void") || VarSolver.typeSet.contains(first))
					return _function(cmd.substring(spaceIndex).strip());
			}
			if(cmd.contains("("))
				return _functionCall(cmd);
		}

		return null;
	}

	private ArrayList<Instruction> _raw(String cmd) {
		return Utils.listOf(Instruction.stringToInstruction(cmd.strip(), str -> Err.normal("Raw instruction syntax Error.")));
	}

	private ArrayList<Instruction> _functionCall(String cmd) {
		String functionName = cmd.substring(0, cmd.indexOf('(')).strip();
		String args = cmd.substring(cmd.indexOf('('), cmd.lastIndexOf(')') + 1).strip();

		String[] split = Utils.getArrayElementsFromStringIgnoreBrackets(args, '(', ')', ',');
		int len = split.length;
		if(args.isBlank())
			len = 0;

		Function func = functions.getFunction(functionName, len);
		FunctionCallField[] fields = new FunctionCallField[len];
		for (int i = 0; i < len; i++)
			fields[i] = func.fields[i].get(split[i], eqsolver);

		FunctionCall call = new FunctionCall(fields, func, eqsolver);
		return call.call();
	}

	private ArrayList<Instruction> _else(String rest) {
		if(!isInElseIfChain)
			throw Err.normal("Cannot use else in non if chains.");

		if(rest.isBlank()) {
			isInElseIfChain = false;
			nextAdd = Bracket._none(new ArrayList<>(), Utils.listOf(Instruction.Pointer(Bracket.getElseIfPointer())));
			Bracket.elseIfIndex++;
			return new ArrayList<>();
		}
		rest = rest.strip();
		if(rest.startsWith("if"))
			return _if(rest.substring(2).strip());
		else
			throw Err.normal("Syntax Error.");
	}

	private ArrayList<Instruction> _function(String rest) {
		int bracketIndex = rest.indexOf('(');
		assert bracketIndex != -1;
		String name = rest.substring(0, bracketIndex);
		int argsLen = Utils.getArrayElementsFromString(rest.substring(bracketIndex - 1), '(', ')', ",").length;
		UserFunction func = (UserFunction) functions.getFunction(name, argsLen);

		nextAdd = Bracket._func(func);
		nextAdd.onBracket = () -> { func.initVariables(ram); };
		return new ArrayList<>();
	}

	private ArrayList<Instruction> _for(String rest) {
		if(!rest.startsWith("(") || !rest.endsWith(")"))
			throw Err.normal("While statement has to start with '(' and end with ')'.");

		rest = rest.substring(1, rest.length() - 1).strip();

		String[] split = rest.split(";", -1);
		if(split.length != 3)
			throw Err.normal("For requires 3 fields.");
		String init = split[0].strip();
		String condi = split[1].strip();
		String addi = split[2].strip();
		nextAdd = Bracket._for();
		ArrayList<Instruction> startList = nextAdd.startList;

		if(!init.isBlank()) {
			ArrayList<Instruction> initSolved = varsolver.cmd(init, false);
			if(initSolved == null)
				throw Err.normal("Syntax Error at for init field.");
			startList.addAll(initSolved);
		}
		if(!condi.isBlank())
			startList.addAll(solveIfEq(condi, nextAdd.endPointer.arg[0].str(), false));
		startList.add(nextAdd.loopPointer);

		ArrayList<Instruction> endList = nextAdd.endList;
		endList.add(nextAdd.checkPointer);

		if(!addi.isBlank()) {
			ArrayList<Instruction> addiSolved = varsolver.cmd(addi, true);
			if(addiSolved == null)
				throw Err.normal("Syntax Error at for addition field.");
			endList.addAll(addiSolved);
		}
		
		if(condi.isBlank()) {
			endList.addAll(Utils.listOf(Cell_Jump(nextAdd.loopPointer.arg[0].str())));
		} else
			endList.addAll(solveIfEq(condi, nextAdd.loopPointer.arg[0].str(), true));

		return new ArrayList<>();
	}

	private ArrayList<Instruction> _while(String rest) {
		if(!rest.startsWith("(") || !rest.endsWith(")"))
			throw Err.normal("While statement has to start with '(' and end with ')'.");

		rest = rest.substring(1, rest.length() - 1).strip();

		nextAdd = Bracket._while();
		nextAdd.startList.addAll(solveIfEq(rest, nextAdd.endPointer.arg[0].str(), false));
		nextAdd.startList.add(nextAdd.loopPointer);
		nextAdd.endList.add(nextAdd.checkPointer);
		nextAdd.endList.addAll(solveIfEq(rest, nextAdd.loopPointer.arg[0].str(), true));
		if(nextAdd.endList.size() == 1)
			nextAdd.endList.add(Cell_Jump(nextAdd.startPointer.arg[0].str()));
		return new ArrayList<>();
	}

	private ArrayList<Instruction> _if(String rest) {
		if(!rest.startsWith("(") || !rest.endsWith(")"))
			throw Err.normal("If statement has to start with '(' and end with ')'.");

		rest = rest.substring(1, rest.length() - 1).strip();
		nextAdd = Bracket._if();
		solveIfEqElse(rest, nextAdd.endPointer.arg[0].str(), false);

		return new ArrayList<>();
	}

	private ArrayList<Instruction> solveIfEq(String eq, String pointerToJump, boolean revertOperator) {
		if(eq.equals("false") || eq.equals("0"))
			return Utils.listOf(Cell_Jump(pointerToJump));
		if(eq.equals("true") || eq.equals("1"))
			return new ArrayList<>();

		ArrayList<Instruction> list = new ArrayList<>();

		int index = -1;
		String ope = null;
		for (String ope1 : booleanOperators) {
			index = eq.indexOf(ope1);
			if(index != -1) {
				ope = ope1;
				break;
			}
		}
		if(index == -1)
			throw Err.normal("Unknown boolean operator in: \"" + eq + "\".");

		String f1 = eq.substring(0, index).strip();
		String f2 = eq.substring(index + ope.length()).strip();

		if(revertOperator)
			ope = revertOperator(ope);

		var t1 = eqsolver.solve(f1);
		boolean nc1 = t1.getValue1() == null;
		list.addAll(t1.getValue3());

		var t2 = eqsolver.solve(f2);
		boolean nc2 = t2.getValue1() == null;
		if(nc1 && nc2 && t2.getValue2() == t1.getValue2()) {
			t2.setValue2(ram.IF_TEMP2);
			list.addAll(eqsolver.solve(f2, ram.IF_TEMP2));
		} else
			list.addAll(t2.getValue3());

		if(!nc1 && !nc2) {
			double val1 = t1.getValue1();
			double val2 = t2.getValue1();
			if(switch (ope) {
				case "==" -> val1 == val2;
				case "!=" -> val1 != val2;
				case ">=" -> val1 >= val2;
				case "<=" -> val1 <= val2;
				case ">" -> val1 > val2;
				case "<" -> val1 < val2;
				default -> throw Err.notPossible();
			})
				return new ArrayList<>();
			return Utils.listOf(Cell_Jump(pointerToJump));
		}

		if(!nc1)
			list.add(If(revertOperator(ope), t2.getValue2(), false, t1.getValue1(), pointerToJump));
		else
			list.add(If(ope, t1.getValue2(), nc2, nc2 ? t2.getValue2() : t2.getValue1(), pointerToJump));
		return list;
	}

	private boolean lookForElse(int line) {
		int bracket = 0;
		for (int i = ++line; i < cmd.length; i++) {
			String l = cmd[i];
			if(l.equals("{"))
				bracket++;
			else if(l.equals("}"))
				bracket--;
			else if(bracket == 0)
				return l.startsWith("else");
		}
		return false;
	}

	private void returnFromIfEqElse(boolean isElse, ArrayList<Instruction> startList, ArrayList<Instruction> endList) {
		if(!isElse) {
			if(isInElseIfChain) {
				isInElseIfChain = false;
				endList.add(Instruction.Pointer(Bracket.getElseIfPointer()));
				Bracket.elseIfIndex++;
				nextAdd.startList.addAll(startList);
				nextAdd.endList.addAll(endList);
			} else {
				nextAdd.startList.addAll(startList);
				nextAdd.endList.addAll(endList);
			}
		} else {
			isInElseIfChain = true;
			endList.add(0, Instruction.Cell_Jump(Bracket.getElseIfPointer()));
			nextAdd.startList.addAll(startList);
			nextAdd.endList.addAll(endList);
		}
	}

	private void solveIfEqElse(String eq, String pointerToJump, boolean revertOperator) {
		boolean isElse = lookForElse(line);

		if(eq.equals("false") || eq.equals("0")) {
			returnFromIfEqElse(isElse, Utils.listOf(Cell_Jump(pointerToJump)), null);
			return;
		}
		if(eq.equals("true") || eq.equals("1")) {
			returnFromIfEqElse(isElse, new ArrayList<>(), null);
			return;
		}

		ArrayList<Instruction> list = new ArrayList<>();
		int index = -1;
		String ope = null;
		for (String ope1 : booleanOperators) {
			index = eq.indexOf(ope1);
			if(index != -1) {
				ope = ope1;
				break;
			}
		}
		if(index == -1)
			throw Err.normal("Unknown boolean operator in: \"" + eq + "\".");

		if(revertOperator)
			ope = revertOperator(ope);

		String f1 = eq.substring(0, index).strip();
		String f2 = eq.substring(index + ope.length()).strip();

		var t1 = eqsolver.solve(f1);
		boolean nc1 = t1.getValue1() == null;
		list.addAll(t1.getValue3());

		var t2 = eqsolver.solve(f2);
		boolean nc2 = t2.getValue1() == null;
		if(nc1 && nc2 && t2.getValue2() == t1.getValue2()) {
			t2.setValue2(ram.IF_TEMP2);
			list.addAll(eqsolver.solve(f2, ram.IF_TEMP2));
		} else
			list.addAll(t2.getValue3());

		if(!nc1 && !nc2) {
			double val1 = t1.getValue1();
			double val2 = t2.getValue1();
			if(switch (ope) {
				case "==" -> val1 == val2;
				case "!=" -> val1 != val2;
				case ">=" -> val1 >= val2;
				case "<=" -> val1 <= val2;
				case ">" -> val1 > val2;
				case "<" -> val1 < val2;
				default -> throw Err.notPossible();
			}) {
				returnFromIfEqElse(isElse, new ArrayList<>(), null);
				return;
			}
			returnFromIfEqElse(isElse, Utils.listOf(Cell_Jump(pointerToJump)), null);
			return;
		}

		if(!nc1)
			list.add(If(revertOperator(ope), t2.getValue2(), false, t1.getValue1(), pointerToJump));
		else
			list.add(If(ope, t1.getValue2(), nc2, nc2 ? t2.getValue2() : t2.getValue1(), pointerToJump));
		returnFromIfEqElse(isElse, list, new ArrayList<>());
	}

	private String revertOperator(String ope) {
		return switch (ope) {
			case "==", "!=" -> ope;
			case ">" -> "<=";
			case "<" -> ">=";
			case ">=" -> "<";
			case "<=" -> ">";
			default -> throw Err.notPossible();
		};
	}

	private ArrayList<Instruction> _continue(String rest) {
		int count = 1;
		if(!rest.isBlank()) {
			double val = ram.solveFinalEq(rest);
			if(val % 1 != 0)
				throw Err.normal("Continure count has to be an integer.");
			count = (int) val;
		}
		Bracket br = lookFor(count, Set.of(For, While));

		return Utils.listOf(Cell_Jump(br.type == For ? br.checkPointer.arg[0].str() : br.startPointer.arg[0].str()));

	}

	private ArrayList<Instruction> _return(String rest) {
		try {
			lookFor(1, Set.of(Function));
		} catch (Exception e) {
			throw Err.normal("Cannot return outside of functions.");
		}
		ArrayList<Instruction> list = new ArrayList<>();
		if(!rest.isBlank()) {
			list.addAll(eqsolver.solve(rest.strip(), IGB_MA.FUNC_RETURN));
		}
		list.add(Cell_Return());
		return list;
	}

	private ArrayList<Instruction> _break(String rest) {
		int index = 1;
		if(!rest.isBlank()) {
			double val = ram.solveFinalEq(rest);
			if(val % 1 != 0)
				throw Err.normal("Break count has to be an integer.");
			index = (int) val;
		}
		Bracket br = getBracket(index);
		return Utils.listOf(Cell_Jump(br.endPointer.arg[0].str()));
	}

	private ArrayList<Instruction> _redo(String rest) {
		int index = 1;
		if(!rest.isBlank()) {
			double val = ram.solveFinalEq(rest);
			if(val % 1 != 0)
				throw Err.normal("Redo count has to be an integer.");
			index = (int) val;
		}
		Bracket br = getBracket(index);
		return Utils.listOf(Cell_Jump(br.startPointer.arg[0].str()));
	}

}
