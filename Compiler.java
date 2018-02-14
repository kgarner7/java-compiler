import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.*;

public class Compiler
{
    /**handles and stores memory*/
    private HashMap<String, VariableData> variableTable = new HashMap<>();
    private HashMap<String, Integer> methodLineTable = new HashMap<>();

    /**line & character index*/
    private int lineNum = 0, charIndex = 0, temp;
    private int nestingLevel = 0, actualNestingLevel = 0, methodLevel = -1;
    /**the lookahead character*/
    private char look = ' ';

    /**the code in list form by line*/
    private ArrayList<String> code = new ArrayList<>();
    /**a stack of the nesting, telling whether a LOOP or an IFELSE*/
    private LinkedList<NestLevel> nestLevel = new LinkedList<>();
    /**used to read from the text*/
    private Scanner in;

    private Math math = new Math();
    private BooleanParser booleanParser = new BooleanParser();
    private VariableParser variableParser = new VariableParser();
    private StatementParser statementParser = new StatementParser();
    private MethodHandler methodHandler = new MethodHandler();
    private RAM memory = new RAM();

    Compiler(String filename){
        try {
            in = new Scanner(new FileReader(new File(filename)));
        } catch (FileNotFoundException ignored) {}

        while (in.hasNextLine())
            code.add(in.nextLine());

        scanMethod(code);
        program();
    }

    /**
     * gets the lookahead character
     */
    private void getChar(){
        if (lineNum >= code.size())
            exception("no ']' found");

        if (charIndex >= code.get(lineNum).length()){
            lineNum++;

            if (lineNum >= code.size())
                exception("no ']' found");

            charIndex = 0;
        }

        while (code.get(lineNum).length() == 0) {
            lineNum++;
            if (lineNum >= code.size())
                exception("no ']' found");
        }

        look = code.get(lineNum).charAt(charIndex);
        charIndex++;
    }

    private void exception(String string){
        System.out.print("\n\nError at line " + (lineNum + 1) + ":" + string + ".");
        System.exit(0);
    }

    private void expected(String given, String string){
        exception("\"" + given + "\" given \"" + string + "\" expected");
    }

    /**
     * checks if a variable exists, else throws error
     */
    private String checkVariable(String variableName){
        if (getInMethod(variableName) == null)
            expected(variableName, "existing variable");
        return getAppropriateName(variableName);
    }

    private void match(char c, boolean matchString){
        if (look == c) {
            getChar();

            if (!matchString)
                skipWhite();
        }
        else
            expected(look + "", c + "");
    }

    private void matchString(String string){
        for (int i = 0; i < string.length(); i++)
            match(string.charAt(i), true);
        skipWhite();
    }

    private void skipWhite(){
        while (look == ' ')
            match(' ', false);
    }

    private void program(){
        matchString("program");
        nestLevel.add(new NestLevel("METHOD", "START", true));
        match('[', true);
        topDeclarations();
        match('[', false);
        operate();
    }

    /**
     * declares universal variables
     */
    private void topDeclarations(){
        while (lineNum != methodLineTable.get("start")){
            if (isAlpha(look)){
                String type = variableParser.getName();
                temp = lineNum;
                String name = "universal." + variableParser.getName();
                if (variableTable.get(name) != null) {
                    lineNum = temp;
                    exception("duplicate variable: " + name.substring(10));
                }

                match('=',false);
                Object value = getValue(type);

                memory.allocateMemory(name, type, value + "");
                variableTable.put(name, new VariableData(-1, type));

                while (look == ','){
                    match(',',false);
                    temp = lineNum;
                    name = "universal." + variableParser.getName();

                    if (variableTable.get(name) != null) {
                        lineNum = temp;
                        exception("duplicate variable: " + name.substring(10));
                    }

                    match('=',false);
                    value = getValue(type);
                    memory.allocateMemory(name, type, value.toString());
                    variableTable.put(name, new VariableData(-1, type));
                }
            }
            else if (isMatchingString("//")){
                lineNum++;
                charIndex = 0;
                getChar();
            }
            else
                getChar();
        }
        skipWhite();
        matchString("command");
        matchString("start");
        matchString("(");
        matchString(")");
    }

    private void operate(){
        nestingLevel++;
        actualNestingLevel++;
        while (look != ']')
        {
            if (isMatchingString("output ")){
                matchString("output");
                output();
            }
            else if (isMatchingString("outputNL ")){
                matchString("outputNL");
                outputLine();
            }
            else if (isMatchingString("if ") || isMatchingString("if("))
                statementParser.doIf();
            else if (isMatchingString("while ") || isMatchingString("while("))
                statementParser.whileLoop();
            else if (parseName(charIndex - 1).equals("break")){
                if (!statementParser.breakLoop())
                    exception("break must be in loop");
                else
                    break;
            }
            else if (isMatchingString("for ") || isMatchingString("for("))
                statementParser.forLoop();
            else if (isMatchingString("do ") || isMatchingString("do["))
                statementParser.doWhileLoop();
            else if (isMatchingString("give")) {
                if (nestLevel.contains(new NestLevel("METHOD",true))){
                    while (!nestLevel.peek().type.equals("METHOD"))
                        nestLevel.pop();
                    nestLevel.pop().running = false;
                    return;
                }
                else {
                    System.exit(0);
                }
            }
            else if(isAlpha(look)){
                String potential = parseName(charIndex - 1);
                if (isPrimitiveType(potential) || getInMethod(potential) != null) {
                    String potentiallyAType = variableParser.getName();
                    if (!isPrimitiveType(potentiallyAType))
                        potentiallyAType = getVariableMethodName(potentiallyAType);
                    if (look == '=') {
                        potentiallyAType = checkVariable(potentiallyAType);
                        assignment(potentiallyAType, variableTable.get(potentiallyAType).type, true);
                    } else {
                        assignment(variableParser.getName(), potentiallyAType, false);
                    }
                }
                else {
                    methodHandler.callMethod(variableParser.getName());
                }
            }
            else {
                switch (look){
                    case '/':
                        matchString("//");
                        lineNum++;
                        charIndex = 0;
                        getChar();
                        break;
                    case ' ':
                        match(' ', false);
                        break;
                    default:
                        exception("unknown character \"" + look + "\" given");
                }
            }
        }
        HashMap<String, VariableData> variableTableCopy = new HashMap<>(variableTable);
        variableTable.keySet().stream().filter(name -> variableTable.get(name).level >= actualNestingLevel).forEach(name -> {
            variableTableCopy.remove(name);
            memory.deleteValue(name);
        });
        variableTable = variableTableCopy;
        nestingLevel--;
        actualNestingLevel--;
    }

    private boolean isNumeric(char c){
        return "0123456799".contains(c + "");
    }

    private boolean isAlpha(char c){
        return "ABCDEFGHIJKLMNOPQRSTUVWXYZ".contains((c + "").toUpperCase());
    }

    private boolean isBoolean(){
        return isMatchingString("true") || isMatchingString("false");
    }

    private String parseName(int index){
        StringBuilder st = new StringBuilder();

        while (index < code.get(lineNum).length() && code.get(lineNum).charAt(index) != ' ') {
            st.append(code.get(lineNum).charAt(index));
            index++;
        }

        return st.toString();
    }

    private boolean isBooleanExpression(){
        int index = charIndex - 1;
        int expectedLineNum = lineNum;
        while (index < code.get(lineNum).length() && expectedLineNum == lineNum){
            if (">!<=&|".contains(code.get(lineNum).charAt(index) + ""))
                return true;
            else if (isAlpha(code.get(lineNum).charAt(index))){
                String name = parseName(index);
                if (name.equals("true") || name.equals("false"))
                    return true;
                else if (getInMethod(name) != null && getInMethod(name).type.equals("boolean"))
                    return true;
            }
            index++;
        }
        return false;
    }

    /**
     * returns whether the next characters match keyWord
     * @param keyWord the String to be compared
     * @return whether the next characters of length keyWord match exactly
     */
    private boolean isMatchingString(String keyWord){

        for (int i = 0; i < keyWord.length(); i++) {
            try {
                if (code.get(lineNum).length() < keyWord.length() || code.get(lineNum).charAt(charIndex - 1 + i) != keyWord.charAt(i))
                    return false;
            }catch (StringIndexOutOfBoundsException e){
                return false;
            }
        }
        return true;
    }

    /**
     * @param name the name of the variable to be assigned
     */
    private void assignment(String name, String type, boolean reassign){
        match('=', false);
        if (getInMethod(name) != null && !reassign)
            exception("variable " + name + " already exists");

        Object value = getValue(type);

        if (type.equals("double") && value.toString().contains("E")) {
            int multValue = Integer.parseInt(value.toString().substring(value.toString().indexOf("E") + 1));
            StringBuilder doubleReplica = new StringBuilder(value.toString().substring(0,value.toString().indexOf("E")));
            int decimalIndex = doubleReplica.indexOf(".");
            doubleReplica.deleteCharAt(doubleReplica.indexOf("."));

            for (int i = 0; i < multValue; i++) {
                decimalIndex++;
                if (decimalIndex >= doubleReplica.length())
                    doubleReplica.insert(doubleReplica.length(),0);
            }

            doubleReplica.insert(decimalIndex, '.');
            value = doubleReplica.toString();
        }

        if (!reassign)
            name = getAppropriateName(name);
        else
            name = getVariableMethodName(name);


        int caseNum = memory.allocateMemory(name,type,value.toString());

        if (caseNum == -1)
            exception("out of memory");
        else if (caseNum == -2)
            exception("incorrect input data for " + name + " for type " + type);

        variableTable.put(name, new VariableData(reassign ? variableTable.get(name).level : actualNestingLevel, type));
    }

    /**
     * gets the assignment part of an assignment statement for type "type"
     * @param type the assignment type expected
     * @return the "type" value of the assignment
     */
    private Object getValue(String type){
        Object value = "";
        switch (type) {
            case "integer":
            case "double":
                Object mathValue = math.expression();
                if (mathValue instanceof Double && type.equals("integer")) {
                    if ((double) mathValue % 1 == 0)
                        value = (int) java.lang.Math.round((double)mathValue);
                    else
                        expected(mathValue + "", "integer");
                } else if (mathValue instanceof Integer && type.equals("double"))
                    value = (int) mathValue * 1.0;
                else
                    value = mathValue;
                break;
            case "char":
                value = variableParser.getCharacter();
                break;
            case "word":
                value = variableParser.getString();
                break;
            case "boolean":
                value = booleanParser.booleanExpression();
                break;
            default:
                exception("unknown type: " + type);
                break;
        }
        if (value == null)
            exception("nothing given");
        return value;
    }

    /**
     * handles output on same line
     */
    private void output(){
        if (isBooleanExpression())
            System.out.print(booleanParser.booleanExpression());
        else if (look == '(' || isNumeric(look) || (variableTable.get(parseName(charIndex - 1)) != null && "intdouble".contains(variableTable.get(parseName(charIndex - 1)).type)))
            System.out.print(math.expression());
        else if (isAlpha(look)){
            int expectedLineNum = lineNum;
            String name = getVariableMethodName(variableParser.getName());
            if (expectedLineNum != lineNum) {
                System.out.print(memory.getValue(name));
            }else if ((variableTable.get(name).type).equals("integer") || variableTable.get(name).type.equals("double")) {
                System.out.print(math.expression());
            } else if (variableTable.get(name).type.equals("boolean")) {
                System.out.print(booleanParser.booleanExpression());
            }
        }
        else if (look == '\"' || look == '\''){
            char charToMatch = look;
            match(charToMatch, true);
            StringBuilder stuff = new StringBuilder();
            int expectedLineNum = lineNum;
            while (look != '\"' && expectedLineNum == lineNum){
                stuff.append(look);
                getChar();
            }
            if (expectedLineNum != lineNum)
                exception("Missing \"");
            System.out.print(stuff);
            match(charToMatch,true);
        }
        else
            exception("unknown type given");
    }

    /**
     * does output and newLine
     */
    private void outputLine(){
        output();
        System.out.println();
    }

    /**
     * handles that annoying typecasting
     * @param value the value to be converted to a double
     * @return value as a double
     */
    private double integerToDouble(Object value){
        return (value instanceof Double) ? (double) value: (int) value * 1.0;
    }

    /**
     * handles getting individual type input from code
     */
    private class VariableParser{
        /**
         * gets a number
         * @return a number
         * @error if a non-digit is used
         */
        private Object getNumber() {
            if (!isNumeric(look) && look != '.') {
                expected(look + "", "number");
            }
            Object value = 0;
            int expectedLineNum = lineNum;
            while (isNumeric(look) && lineNum == expectedLineNum) {
                value = 10 * (int) value + Integer.parseInt(look + "");
                getChar();
            }

            if (look == '.') {
                value = (int) value * 1.0;
                match('.',false);
                int count = 0;
                while (isNumeric(look) && lineNum == expectedLineNum) {
                    value = (double) value * 10.0 + Double.parseDouble(look + "");
                    count++;
                    getChar();
                }
                StringBuilder number = new StringBuilder(value + "");
                for (int i = 0; i < count; i++) {
                    int pointIndex = number.indexOf(".");
                    if (pointIndex == 0){
                        number.insert(0,'0');
                        pointIndex++;
                    }
                    char num = number.charAt(pointIndex - 1);
                    number.setCharAt(pointIndex,num);
                    number.setCharAt(pointIndex - 1, '.');
                }
                value = Double.parseDouble(number.toString());
            }
            skipWhite();
            return value;
        }

        /**
         * gets a String
         * @return a singular String
         * @error if the first character is not a number
         */
        private String getString(){
            match('"',false);
            if (look == '"'){
                match('"',false);
                return "";
            }
            if (!isAlpha(look)) {
                expected(look + " ", "alphabetic character expected");
                return "";
            }
            else {
                StringBuilder tempValue = new StringBuilder();
                while (look != '"'){
                    tempValue.append(look);
                    getChar();
                }
                match('"',false);
                return tempValue.toString();
            }
        }

        /**
         * gets a character
         * @return the character represented by code
         * @error if the format does not match ' + character + '
         */
        private char getCharacter(){
            match('\'',false);
            char value;
            if (look == '\\') {
                match('\\',false);
                value = (char) ('\\' + look);
            } else
                value = look;
            getChar();
            match('\'',false);
            return value;
        }

        /**
         * gets a character sequence from the code
         * @return the character sequence
         * @error if there is a non alphanumeric character
         */
        private String getName(){
            StringBuilder token = new StringBuilder();
            if (!isAlpha(look)){
                expected(look + "", "Name");
            }
            else {
                int expectedLineNum = lineNum;
                while ((isAlpha(look) || isNumeric(look)) && expectedLineNum == lineNum) {
                    token.append(look);
                    getChar();
                }
                skipWhite();
            }
            return token.toString();
        }
    }

    /**
     * stores memory and stuff
     */
    private class VariableData{
        private int level;
        private String type;

        private VariableData(int level, String type){
            this.level = level;
            this.type = type;
        }

        public String toString(){
            return type + ":" + level;
        }
    }

    /**
     * MATHS
     */
    private class Math {

        /**
         * handles addition and subtraction
         *
         * @return the result of adding and subtracting factors
         * @error if the type expected is not an int or a double
         */
        private Object expression() {
            return calculateInfixValue();
        }

        private Object calculatePostFix(String postFix)
        {
            Stack<Object> doubleStack = new Stack<>();
            String[] split = postFix.split(" ");

            for (String aSplit : split) {
                if ("+*-/%".contains(aSplit)) {
                    if (doubleStack.empty())
                        exception("malformed math expression");

                    Object val1 = doubleStack.pop();

                    if (doubleStack.empty())
                        exception("malformed math expression");

                    Object val2 = doubleStack.pop();
                    Object total;

                    switch (aSplit) {
                        case "+":
                            if (val1 instanceof Integer && val2 instanceof Integer)
                                total = (int) val1 + (int) val2;
                            else
                                total = integerToDouble(val1) + integerToDouble(val2);
                            break;
                        case "-":
                            if (val1 instanceof Integer && val2 instanceof Integer)
                                total = (int) val2 - (int) val1;
                            else
                                total = integerToDouble(val2) - integerToDouble(val1);
                            break;
                        case "*":
                            if (val1 instanceof Integer && val2 instanceof Integer)
                                total = (int) val1 * (int) val2;
                            else
                                total = integerToDouble(val1) * integerToDouble(val2);
                            break;
                        case "%":
                            if (val1 instanceof Integer && val2 instanceof Integer)
                                total = (int) val2 % (int) val1;
                            else
                                total = integerToDouble(val2) % integerToDouble(val1);
                            break;
                        default:
                            if (Double.parseDouble(val1.toString()) == 0)
                                exception("Don't break math");
                            if (val1 instanceof Integer && val2 instanceof Integer)
                                total = (int) val2 / (int) val1;
                            else
                                total = integerToDouble(val2) / integerToDouble(val1);
                            break;
                    }
                    doubleStack.push(total);
                } else{
                    if (isInteger(aSplit))
                        doubleStack.push(Integer.parseInt(aSplit));
                    else
                        doubleStack.push(Double.parseDouble(aSplit));
                }

            }
            Object d = doubleStack.pop();
            skipWhite();
            if(!doubleStack.empty()) {
                exception("malformed math expression");
                return -1;
            }
            else
                return d;
        }

        private Object calculateInfixValue()
        {
            return calculatePostFix(convertInfixToPostFix());
        }

        private String convertInfixToPostFix()
        {
            StringBuilder postFix = new StringBuilder("");
            Stack<String> stack = new Stack<>();
            int expectedLineNum = lineNum;
            int difference = 0;

            while (expectedLineNum == lineNum && (getOpValue(look + "") != 4 || isAlpha(look) || isNumeric(look)))
            {
                if("+*-/*%()".contains(look + ""))
                {
                    if(look == '(') {
                        stack.push(look + "");
                        difference++;
                    }
                    else if (look == ')'){
                        difference--;
                        if (difference < 0)
                            break;
                        while (!stack.peek().equals("(")) {
                            postFix.append(stack.pop());
                            postFix.append(" ");
                        }
                        stack.pop();
                    }
                    else if(stack.empty())
                        stack.push(look + "");
                    else if(getOpValue(stack.peek()) < getOpValue(look + ""))
                        stack.push(look + "");
                    else
                    {
                        while(!stack.empty() && getOpValue(stack.peek()) >= getOpValue(look + ""))
                        {
                            postFix.append(stack.pop());
                            postFix.append(" ");
                        }
                        stack.push(look + "");
                    }
                    getChar();
                }
                else if (isAlpha(look))
                {
                    int temp = lineNum;
                    String name = getVariableMethodName(variableParser.getName());

                    if (!variableTable.get(getVariableMethodName(name)).type.equals("integer") && !variableTable.get(getVariableMethodName(name)).type.equals("double")) {
                        lineNum = temp;
                        exception("not numeric");
                    }
                    postFix.append(memory.getValue(getVariableMethodName(name)).toString()).append(" ");
                }
                else if (isNumeric(look)){
                    Object value = variableParser.getNumber();
                    postFix.append(value).append(" ");
                }
                else{
                    expected(look + "", "math expression");
                }
                if (look == ' ') {
                    skipWhite();
                }
            }

            while(!stack.empty()) {
                if (postFix.length() == 0)
                    expected(look + "", "math expression");
                else if(!postFix.substring(postFix.length() - 1).equals(" "))
                    postFix.append(" ");
                postFix.append(stack.pop());
            }
            return postFix.toString();
        }

        private int getOpValue(String s)
        {
            if("+-".contains(s))
                return 1;
            else if("*/%".contains(s))
                return 2;
            else if("()".contains(s))
                return -1;
            else
                return 4;
        }

        private boolean isInteger(String string){
            try {
                Integer.parseInt(string);
                return true;
            }catch (IllegalArgumentException e){
                return false;
            }
        }
    }

    /**
     * used for ordering of LOOPS, METHODS and MORE
     */
    private class NestLevel {
        /**type of nesting*/
        private String type, name = "";
        /**whether the nesting as been broken*/
        private boolean running;

        NestLevel(String type, boolean running){
            this.type = type;
            this.running = running;
        }

        NestLevel(String type, String name, boolean running){
            this(type, running);
            this.name = name;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof NestLevel))
                return false;
            NestLevel nestLevel = (NestLevel) obj;
            return nestLevel.type.equals(type);
        }

        @Override
        public String toString() {
            return type + " " + running  + " " + name;
        }
    }

    /**
     * handles parsing of boolean and booelan expressions
     */
    private class BooleanParser{
        /**
         * returns a plain boolean
         * @return either true or false, respective of input
         * @error if the input is not a boolean
         */
        private boolean getBoolean(){
            if (!isBoolean())
                expected(look + "", "boolean");
            boolean toReturn = isMatchingString("true");
            matchString(toReturn ? "true": "false");
            return toReturn;
        }

        /**
         * handles the "or" boolean operations
         * @return the result of calling all the "or" operations
         */
        private boolean booleanExpression(){
            boolean boolValue = boolTerm();
            while (look == '|'){
                switch (look){
                    case '|':
                        match('|', false);
                        boolean secondBool = boolTerm();
                        boolValue = boolValue || secondBool;
                        break;
                    default:
                        expected(look + "", "|");
                }
            }
            return boolValue;
        }

        /**
         * handles "and" boolean operations
         * @return the result of all the "and" operations
         */
        private boolean boolTerm(){
            boolean value = notFactor();
            while (look == '&'){
                match('&', false);
                boolean tempValue = notFactor();
                value = value && tempValue;
            }
            return value;
        }

        /**
         * nots a boolean expression
         * @return the notted version of the expression
         */
        private boolean notFactor(){
            boolean notNot = true;
            boolean value;
            while (look == '!') {
                match('!',false);
                notNot = !notNot ;
            }
            if (look == '(') {
                match('(', false);

                value = booleanExpression();
                match(')',false);
            }
            else
                value = booleanFactor();
            return notNot ? value: !value;
        }

        /**
         * chooses whether to get a plain boolean or boolean expression
         * @return the result of the expression or the plain boolean
         */
        private boolean booleanFactor() {
            if (isBoolean())
                return getBoolean();
            else if (isAlpha(look)){
                String name = getVariableMethodName(parseName(charIndex - 1));
                if ((variableTable.get(name).type).equals("integer") || (variableTable.get(name).type).equals("double"))
                    return mathRelation();
                else if ((variableTable.get(name).type).equals("word") || variableTable.get(name).type.equals("char")){
                    variableParser.getName();
                    match('=',false);
                    Object secondValue;

                    if (look == '"' && variableTable.get(name).type.equals("word") || look == '\'' && variableTable.get(name).type.equals("char"))
                        secondValue = variableTable.get(name).type.equals("word") ? variableParser.getString(): variableParser.getCharacter();
                    else {
                        String tempName = getVariableMethodName(variableParser.getName());

                        if (!variableTable.get(tempName).type.equals(variableTable.get(name).type))
                            expected(variableTable.get(tempName).type,variableTable.get(name).type);
                        secondValue = memory.getValue(tempName);
                    }

                    return memory.getValue(name).equals(secondValue);
                }
                else {
                    name = getVariableMethodName(variableParser.getName());
                    return (boolean) memory.getValue(name);
                }
            }
            else if (isNumeric(look))
                return mathRelation();
            else if (look == '"' || look == '\''){
                String type = look == '"' ? "word": "char";
                Object value1 = type.equals("word") ? variableParser.getString(): variableParser.getCharacter();
                match('=', false);
                Object value2;

                if (look == '"' && type.equals("word") || look == '\'' && type.equals("char"))
                    value2 = type.equals("word") ? variableParser.getString(): variableParser.getCharacter();
                else {
                    String varName = getVariableMethodName(variableParser.getName());

                    if (!variableTable.get(varName).type.equals(type))
                        expected(variableTable.get(varName).type,type);
                    skipWhite();
                    value2 = memory.getValue(varName);
                }

                return value1.equals(value2);
            }
            else {
                exception("something went wrong");
                return false;
            }
        }

        /**
         * performs mathematical comparisons and returns the result
         * @return whether the relation is true or false
         * @error if we are not given a numeric comparison expression
         */
        private boolean mathRelation(){
            double value = integerToDouble(math.expression());
            if (look == '>' || look == '<' || look == '=' || look == '!'){
                switch (look){
                    case '=': {
                        match('=',false);
                        int caseNum = 0;
                        switch (look){
                            case '>': match('>',false);
                                caseNum = 1;
                                break;
                            case '<': match('<',false);
                                caseNum = -1;
                                break;
                        }
                        double secondValue = integerToDouble(math.expression());
                        skipWhite();
                        return (caseNum == 0) ? value == secondValue: (caseNum == 1)? value >= secondValue: value<=secondValue;
                    }
                    case '!': {
                        match('!',false);
                        match('=',false);
                        double secondValue = integerToDouble(math.expression());
                        skipWhite();
                        return secondValue != value;
                    }
                    case '>': {
                        match('>',false);
                        double secondValue = integerToDouble(math.expression());
                        skipWhite();
                        return value > secondValue;
                    }
                    case '<':{
                        match('<',false);
                        double secondValue = integerToDouble(math.expression());
                        skipWhite();
                        return value < secondValue;
                    }
                    default:{
                        expected(look + "","boolean expression");
                        return false;
                    }
                }
            }else {
                expected(look + "", "boolean expression");
                return false;
            }
        }
    }

    private class StatementParser{
        /**
         * if statement
         */
        private void doIf(){
            matchString("if");

            match('(',false);
            boolean doIf = booleanParser.booleanExpression();
            match(')',false);
            match('[',false);
            if (doIf) {
                NestLevel nestLevelValue = new NestLevel("IFELSE",true);
                nestLevel.push(nestLevelValue);
                operate();

                if (nestLevelValue.running) {
                    nestLevel.pop();
                    match(']',false);
                    if (look == 'e') {
                        while (look == 'e') {
                            while (look != '[')
                                getChar();
                            match('[', false);
                            matchParentheses(1, 0, '[', ']');
                        }
                    }
                }
            } else {
                matchParentheses(1, 0, '[', ']');
                if (isMatchingString("else if")){
                    matchString("else");
                    doIf();
                }
                else if (isMatchingString("else")) {
                    matchString("else");
                    match('[', false);
                    NestLevel nestLevelValue = new NestLevel("IFELSE", true);
                    nestLevel.push(nestLevelValue);
                    operate();

                    if (nestLevelValue.running) {
                        nestLevel.pop();
                        match(']', false);
                    }
                }
            }
        }

        /**
         * performs a while loop
         */
        private void whileLoop(){
            int returnRow = lineNum, returnCharacter = charIndex;
            matchString("while");
            match('(',false);
            boolean conditionOK = booleanParser.booleanExpression();
            match(')',false);
            while (conditionOK){

                match('[', false);
                NestLevel nestLevelValue = new NestLevel("LOOP",true);
                nestLevel.push(nestLevelValue);
                operate();

                if (nestLevelValue.running) {
                    nestLevel.pop();
                    match(']',false);
                    lineNum = returnRow;
                    charIndex = returnCharacter;
                    look = code.get(lineNum).charAt(returnCharacter - 1);
                    matchString("while");
                    match('(',false);
                    conditionOK = booleanParser.booleanExpression();
                    match(')',false);
                }
                else {
                    return;
                }
            }
            match('[',false);
            matchParentheses(1, 0, '[', ']');
        }

        private void forLoop(){
            matchString("for");
            match('(',false);
            String type = variableParser.getName();
            String name;
            boolean temporary = false;

            if (look == '='){
                name = getVariableMethodName(type);
                assignment(name, variableTable.get(name).type, true);
            }
            else {
                name = variableParser.getName();
                assignment(name, type, false);
                temporary = true;
            }

            match(';',false);
            int returnConditionIndex = charIndex - 1, returnConditionLine = lineNum;
            boolean canContinue = booleanParser.booleanExpression();
            match(';',false);

            int returnReassignIndex = charIndex - 1, returnReassignLine = lineNum;
            matchParentheses(1, 0, '(', ')');

            int operateIndex = charIndex - 1, operateLine = lineNum;

            while (canContinue){
                match('[', false);
                NestLevel temp = new NestLevel("LOOP", true);
                nestLevel.push(temp);
                operate();
                if (temp.running) {
                    nestLevel.pop();
                    match(']',false);

                    lineNum = returnReassignLine;
                    charIndex = returnReassignIndex;
                    getChar();

                    String tempName = variableParser.getName();
                    assignment(tempName, variableTable.get(getVariableMethodName(tempName)).type, true);

                    lineNum = returnConditionLine;
                    charIndex = returnConditionIndex;
                    getChar();

                    canContinue = booleanParser.booleanExpression();

                    lineNum = operateLine;
                    charIndex = operateIndex;
                    getChar();
                }
                else
                    return;
            }
            match('[',false);
            matchParentheses(1, 0, '[', ']');

            if (temporary){
                variableTable.remove(name);
                memory.deleteValue(name);
            }
        }

        private void doWhileLoop(){
            matchString("do");
            int operateIndex = charIndex - 1, operateLine = lineNum;
            boolean canContinue;
            do {
                match('[', false);
                NestLevel level = new NestLevel("LOOP",true);
                nestLevel.push(level);
                operate();
                if (level.running) {
                    nestLevel.pop();
                    match(']',false);
                    matchString("while");
                    match('(',false);
                    canContinue = booleanParser.booleanExpression();
                    match(')',false);
                    if (canContinue) {
                        lineNum = operateLine;
                        charIndex = operateIndex;
                        getChar();
                    }

                }
                else {
                    matchString("while");
                    match('(', false);
                    booleanParser.booleanExpression();
                    match(')', false);
                    return;
                }

            }while (canContinue);
        }

        /**
         * ends a loop (or does nothing if no loop exists)
         * @return whether a loop was terminated
         */
        private boolean breakLoop(){
            matchString("break");
            int openCount = 1;

            if (!nestLevel.contains(new NestLevel("LOOP",true)))
                return false;
            else{
                while (!nestLevel.peek().type.equals("LOOP")) {
                    nestLevel.peek().running = false;
                    nestLevel.pop();
                    openCount++;
                }

                nestLevel.pop().running = false;
                matchParentheses(openCount, look == ']' ? 1 : 0, '[', ']');
                return true;
            }
        }
    }

    private class MethodHandler{
        public void callMethod(String name){
            if (methodLevel == -500)
                exception("just stop.");

            int methodCallRow = lineNum, methodCallCol = charIndex;
            int cloneOfNestingLevel = nestingLevel;

            matchParentheses(nestingLevel, look == ']' ? 1 : 0, '[', ']');
            if (!methodLineTable.containsKey(name)) {
                lineNum = methodCallRow - 1;
                exception("unknown characters \"" + name + "\"");
            }

            lineNum = methodLineTable.get(name);
            charIndex = 0;
            getChar();
            skipWhite();

            matchString("command");
            matchString(name + "(");
            ArrayList<StringClass> params = new ArrayList<>();

            while (look != ')') {
                String paramType = variableParser.getName();
                String paramName = variableParser.getName();

                if (paramName.equals("returnValue"))
                    exception("\"returnValue\" is a keyword");
                skipWhite();

                params.add(new StringClass(paramType, paramName));

                if (look == ',')
                    match(',', false);
            }


            match(')', false);
            int methodListRow = lineNum, methodListChar = charIndex;
            lineNum = methodCallRow;
            charIndex = methodCallCol;
            getChar();

            for (int i = 0; i < params.size(); i++){
                StringClass param = params.get(i);
                if (param.type.equals("integer") || param.type.equals("double")){
                    variableTable.put((methodLevel - 1) + "." + param.name, new VariableData(actualNestingLevel, param.type));
                    memory.allocateMemory((methodLevel - 1) + "." + param.name, param.type, (math.expression().toString()));
                }
                else if (look == '\''){
                    char character = variableParser.getCharacter();
                    variableTable.put((methodLevel - 1) + "." + param.name, new VariableData(actualNestingLevel, param.type));
                    memory.allocateMemory((methodLevel - 1) + "." + param.name, param.type, character + "");
                }
                else if (look == '"') {
                    String value = variableParser.getString();
                    variableTable.put((methodLevel - 1) + "." + param.name, new VariableData(actualNestingLevel, param.type));
                    memory.allocateMemory((methodLevel - 1) + "." + param.name, param.type, value);
                }
                else if (param.type.equals("boolean")){
                    String remaining = code.get(lineNum).substring(charIndex - 1);
                    if (i != params.size() - 1)
                        remaining = remaining.substring(0, remaining.indexOf(","));
                    else
                        remaining = remaining.substring(0, remaining.indexOf(")"));

                    if (getInMethod(remaining) != null){
                        remaining = getVariableMethodName(remaining);

                        if (!variableTable.get(remaining).type.equals("boolean"))
                            expected(variableTable.get(remaining).type, "boolean");

                        assignInMethodByName((methodLevel - 1) + "." + param.name, remaining, param.type, true);
                    }
                    else {
                        variableTable.put((methodLevel - 1) + "." + param.name, new VariableData(actualNestingLevel, param.type));
                        memory.allocateMemory((methodLevel - 1) + "." + param.name, param.type, booleanParser.booleanExpression() + "");
                    }
                }
                else{
                    String variableName = checkVariable(variableParser.getName());

                    if (!variableTable.get(variableName).type.equals(param.type))
                        expected(variableTable.get(variableName).type, param.type);
                    skipWhite();

                    variableTable.put((methodLevel - 1) + "." + param.name, new VariableData(actualNestingLevel, param.type));
                    memory.allocateMemory((methodLevel - 1) + "." + param.name, param.type, memory.getValue(variableName).toString());
                }


                if (i != params.size() - 1)
                    match(',',false);
            }


            match(')', false);
            int saveRow = lineNum, saveCol = charIndex;
            lineNum = methodListRow;
            charIndex = methodListChar - 1;

            getChar();
            matchString("gives");
            String type;

            if (isMatchingString("nothing")) {
                type = "nothing";
                matchString("nothing");
            }
            else
                type = variableParser.getName();

            match('[', false);

            LinkedList<NestLevel> cloneOfNestLevel = new LinkedList<>();
            Iterator<NestLevel> iterator = nestLevel.iterator();

            while (iterator.hasNext()){
                NestLevel temp = iterator.next();
                if (!temp.type.equals("METHOD"))
                    iterator.remove();
                cloneOfNestLevel.add(temp);
            }

            NestLevel level = new NestLevel("METHOD", name, true);
            nestLevel.push(level);
            nestingLevel = 0;

            methodLevel--;
            operate();

            if (level.running){
                if (!type.equals("nothing"))
                    exception("no return statement");
                match(']', false);

            }else {
                matchString("give");
                Object value;
                switch (type){
                    case "integer":
                    case "double":
                        value = math.expression();
                        break;
                    case "char":
                        if (isAlpha(look)){
                            String variableName = getVariableMethodName(variableParser.getName());

                            if (!variableTable.get(variableName).type.equals("char"))
                                expected(variableTable.get(variableName).type,"char");
                            skipWhite();

                            value = memory.getValue(variableName);
                        }
                        else
                            value = variableParser.getCharacter();
                        break;
                    case "word":
                        if (isAlpha(look)){
                            String variableName = getVariableMethodName(variableParser.getName());

                            if (!variableTable.get(variableName).type.equals("word"))
                                expected(variableTable.get(variableName).type,"word");
                            skipWhite();

                            value = memory.getValue(variableName);
                        }
                        else
                            value = variableParser.getString();
                        break;
                    case "boolean":
                        value = booleanParser.booleanExpression();
                        break;
                    default:
                        expected(type,"primitive type");
                        value = null;
                        break;
                }
                variableTable.put(name + ".returnValue", new VariableData(-1,type));
                memory.allocateMemory(name + ".returnValue", type, value.toString());
            }

            HashMap<String, VariableData> variableDataHashMap = new HashMap<>(variableTable);
            variableTable.keySet().stream().filter(variableName -> variableName.startsWith((methodLevel) + ".")).forEach(variableName -> {
                variableDataHashMap.remove(variableName);
                memory.deleteValue(variableName);
            });

            variableTable = variableDataHashMap;
            nestLevel = cloneOfNestLevel;
            lineNum = saveRow;
            charIndex = saveCol - 1;
            nestingLevel = cloneOfNestingLevel;

            methodLevel++;
            getChar();
        }
    }

    /**
     * a class used to store parameters
     */
    private class StringClass{
        /**The type and name of the parameter*/
        private String type, name;

        public StringClass(String type, String name){
            this.type = type;
            this.name = name;
        }

        public String toString(){
            return type + " " + name;
        }
    }

    private void scanMethod(ArrayList<String> code){
        while (lineNum < code.size() ){
            if (lineNum == code.size() - 1 && look == ']')
                break;

            getChar();
            if (isMatchingString("command")){
                matchString("command");
                String methodName = variableParser.getName();
                methodLineTable.put(methodName, lineNum);
                lineNum++;
                charIndex = 0;
            }
        }
        lineNum = 0;
        charIndex = 0;
        getChar();
    }

    /**
     * matches open/closed parentheses by skipping between
     * @param openCount the number of open parentheses already traversed
     * @param closedCount the number of closed parentheses already traversed
     * @param open the 'open' character
     * @param close the 'close' character
     */
    private void matchParentheses(int openCount, int closedCount, char open, char close){
        while (openCount != closedCount) {
            getChar();

            if (look == open)
                openCount++;

            if (look == close)
                closedCount++;
        }
        match(close, false);
    }

    private boolean isPrimitiveType(String type){
        return type.equals("boolean") || type.equals("integer") || type.equals("double") || type.equals("char") || type.equals("word");
    }

    /**
     * gets the value stored for variableName at method level (or null, if it doesn't exist
     * @param variableName the name we are searching
     * @return the VariableData associated with variableName at the highest method
     */
    private VariableData getInMethod(String variableName){
        for (NestLevel level: nestLevel){
            if (level.type.equals("METHOD")){
                VariableData data = variableTable.get(level.name.equals("START") || variableName.contains(".") ? variableName : (methodLevel + "." + variableName));
                if (data == null)
                    data = variableTable.get("universal." + variableName);
                return data;
            }
        }

        return null;
    }

    private void assignInMethodByName(String newVariable, String oldVariable, String type, boolean alreadyFormatted){
        if(!alreadyFormatted)
            newVariable = getAppropriateName(newVariable);

        variableTable.put(newVariable,new VariableData(actualNestingLevel,type));

        if (memory.allocateMemoryByMemory(newVariable, getVariableMethodName(oldVariable), type) == -2)
            exception("incorrect format for type \"" + type + "\"");
    }

    private String getVariableMethodName(String variable){
        for (NestLevel level: nestLevel){
            if (level.type.equals("METHOD")){
                if (level.name.equals("START")){
                    if (variableTable.get(variable) == null)
                        variable = "universal." + variable;
                    if (variableTable.get(variable) == null)
                        exception("variable \"" + variable.substring(10) + "\" does not exist");
                    return variable;
                }
                else {
                    if (variableTable.get(variable) == null)
                        variable = methodLevel + "." + variable;
                    if (variableTable.get(variable) == null)
                        variable = "universal." + variable;
                    if (variableTable.get(variable) == null)
                        exception("variable \"" + variable.substring(10) + "\" does not exist");

                    return variable;
                }
            }
        }
        exception("Milwaukee, we have a problem");
        return null;
    }

    private String getAppropriateName(String variableName){
        for (NestLevel level: nestLevel){
            if (level.type.equals("METHOD")){
                if (level.name.equals("START"))
                    return variableName;
                else
                    return methodLevel + "." + variableName;
            }
        }
        return null;
    }
}
