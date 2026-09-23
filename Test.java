package me.jssandoval.lab3;

import java.util.*;
import java.util.stream.Collectors;

public class Test {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Introduce la expresión lógica (o escribe 'salir'): ");
        String entrada = scanner.nextLine().trim();
        System.out.println("Entrada: " + entrada);

        // 1. Shunting Yard: String a Posfijo
        List<String> posfijo = ShuntingYard.convertirAPostfijo(entrada);
        System.out.println("Posfijo: " + posfijo);

        // 2. Construir AST desde Posfijo
        Expr raiz = ShuntingYard.construirAST(posfijo);
        System.out.println("\nAST Original:\n" + raiz.toString());

        // 3. Bucle de simplificación
        List<String> pasos = new ArrayList<>();
        Expr simplificado = raiz;
        String antes;

        do {
            antes = simplificado.toString();
            simplificado = simplificado.simplificar(pasos);
        } while (!antes.equals(simplificado.toString()));

        System.out.println("\n--- Pasos de Simplificación ---");
        for (String paso : pasos) {
            System.out.println(paso);
        }

        System.out.println("\nExpresión Final (Mínima):\n" + simplificado.toString());
    }
}


class ShuntingYard {
    private static int precedencia(char operador) {
        return switch (operador) {
            case '+', '|', '^' -> 1;
            case '*', '&' -> 2;
            case '!', '-' -> 3;
            default -> -1;
        };
    }

    public static List<String> convertirAPostfijo(String infijo) {
        List<String> salida = new ArrayList<>();
        Deque<Character> pila = new ArrayDeque<>();

        for (int i = 0; i < infijo.length(); i++) {
            char c = infijo.charAt(i);

            if (c == ' ') continue;

            if (Character.isLetterOrDigit(c)) {
                salida.add(String.valueOf(c));
            } else if (c == '(') {
                pila.push(c);
            } else if (c == ')') {
                while (!pila.isEmpty() && pila.peek() != '(') {
                    salida.add(String.valueOf(pila.pop()));
                }
                if (!pila.isEmpty() && pila.peek() == '(') {
                    pila.pop();
                }
            } else {
                while (!pila.isEmpty() && precedencia(c) <= precedencia(pila.peek())) {
                    salida.add(String.valueOf(pila.pop()));
                }
                pila.push(c);
            }
        }

        while (!pila.isEmpty()) {
            salida.add(String.valueOf(pila.pop()));
        }

        return salida;
    }

    public static Expr construirAST(List<String> postfijo) {
        Deque<Expr> pila = new ArrayDeque<>();

        for (String token : postfijo) {
            char c = token.charAt(0);

            if (Character.isLetterOrDigit(c)) {
                pila.push(new Var(token));
            } else if (c == '!' || c == '-') {
                Expr operando = pila.pop();
                pila.push(new Not(operando));
            } else {
                // Operadores binarios
                Expr der = pila.pop();
                Expr izq = pila.pop();

                switch (c) {
                    case '&', '*' -> pila.push(new And(izq, der));
                    case '|', '+' -> pila.push(new Or(izq, der));
                    case '^' -> {
                        // Expansión automática de XOR: A ^ B -> (A & !B) | (!A & B)
                        // Esto permite que el AST N-ario lo simplifique con las reglas base
                        Expr and1 = new And(izq, new Not(der));
                        Expr and2 = new And(new Not(izq), der);
                        pila.push(new Or(and1, and2));
                    }
                }
            }
        }
        return pila.pop();
    }
}

abstract class Expr {
    public abstract Expr simplificar(List<String> pasos);
    public abstract String toString();

    public boolean esIgual(Expr otra) {
        return this.toString().equals(otra.toString());
    }
}

class Var extends Expr {
    String nombre;
    Var(String n) { nombre = n; }

    public Expr simplificar(List<String> pasos) { return this; }
    public String toString() { return nombre; }
}

class Not extends Expr {
    Expr hijo;
    Not(Expr h) { hijo = h; }

    public Expr simplificar(List<String> pasos) {
        hijo = hijo.simplificar(pasos);

        // Doble negación
        if (hijo instanceof Not) {
            Expr nieto = ((Not)hijo).hijo;
            pasos.add("Doble negación: " + this + " -> " + nieto);
            return nieto;
        }

        // De Morgan sobre OR
        if (hijo instanceof Or or) {
            List<Expr> negados = or.operandos.stream().map(Not::new).collect(Collectors.toList());
            And nuevoAnd = new And(negados);
            pasos.add("De Morgan: " + this + " -> " + nuevoAnd);
            return nuevoAnd;
        }

        // De Morgan sobre AND
        if (hijo instanceof And and) {
            List<Expr> negados = and.operandos.stream().map(Not::new).collect(Collectors.toList());
            Or nuevoOr = new Or(negados);
            pasos.add("De Morgan: " + this + " -> " + nuevoOr);
            return nuevoOr;
        }

        return this;
    }

    public String toString() {
        if (hijo instanceof Var) return "!" + hijo;
        return "!(" + hijo + ")";
    }
}

class And extends Expr {
    List<Expr> operandos;

    And(Expr... exprs) { operandos = new ArrayList<>(Arrays.asList(exprs)); }
    And(List<Expr> exprs) { operandos = new ArrayList<>(exprs); }

    public boolean contiene(Expr e) {
        return operandos.stream().anyMatch(o -> o.esIgual(e));
    }

    public Expr simplificar(List<String> pasos) {
        for (int i = 0; i < operandos.size(); i++)
            operandos.set(i, operandos.get(i).simplificar(pasos));

        List<Expr> aplanados = new ArrayList<>();
        for (Expr e : operandos) {
            if (e instanceof And) aplanados.addAll(((And)e).operandos);
            else aplanados.add(e);
        }
        if (aplanados.size() != operandos.size()) operandos = aplanados;

        boolean absorbido = false;
        List<Expr> aBorrar = new ArrayList<>();
        for (Expr e : operandos) {
            for (Expr otro : operandos) {
                if (otro instanceof Or or && or.contiene(e)) {
                    aBorrar.add(otro);
                    absorbido = true;
                }
            }
        }
        if (absorbido) {
            operandos.removeAll(aBorrar);
            pasos.add("Absorción: -> " + this);
        }

        if (operandos.size() == 1) return operandos.get(0);
        return this;
    }

    public String toString() {
        return "(" + operandos.stream().map(Expr::toString).collect(Collectors.joining(" & ")) + ")";
    }
}

class Or extends Expr {
    List<Expr> operandos;

    Or(Expr... exprs) { operandos = new ArrayList<>(Arrays.asList(exprs)); }
    Or(List<Expr> exprs) { operandos = new ArrayList<>(exprs); }

    public boolean contiene(Expr e) {
        return operandos.stream().anyMatch(o -> o.esIgual(e));
    }

    public Expr simplificar(List<String> pasos) {
        for (int i = 0; i < operandos.size(); i++)
            operandos.set(i, operandos.get(i).simplificar(pasos));

        List<Expr> aplanados = new ArrayList<>();
        for (Expr e : operandos) {
            if (e instanceof Or) aplanados.addAll(((Or)e).operandos);
            else aplanados.add(e);
        }
        if (aplanados.size() != operandos.size()) operandos = aplanados;

        for (int i = 0; i < operandos.size(); i++) {
            for (int j = i + 1; j < operandos.size(); j++) {
                if (operandos.get(i) instanceof And a1 && operandos.get(j) instanceof And a2) {
                    List<Expr> comunes = new ArrayList<>(a1.operandos);
                    comunes.removeIf(e -> !a2.contiene(e));

                    if (!comunes.isEmpty()) {
                        Expr comun = comunes.get(0);

                        a1.operandos.removeIf(e -> e.esIgual(comun));
                        a2.operandos.removeIf(e -> e.esIgual(comun));

                        Expr resto1 = a1.operandos.size() == 1 ? a1.operandos.get(0) : a1;
                        Expr resto2 = a2.operandos.size() == 1 ? a2.operandos.get(0) : a2;

                        Expr nuevoOr = new Or(resto1, resto2);
                        Expr nuevoAnd = new And(comun, nuevoOr);

                        operandos.set(i, nuevoAnd);
                        operandos.remove(j);
                        pasos.add("Factor común (" + comun + "): -> " + this);
                        return this;
                    }
                }
            }
        }

        if (operandos.size() == 1) return operandos.get(0);
        return this;
    }

    public String toString() {
        return "(" + operandos.stream().map(Expr::toString).collect(Collectors.joining(" | ")) + ")";
    }
}