import java.util.HashMap;

public class RAM
{
    private boolean[] memory = new boolean[800000];
    private int lastMemory = 0;
    private char[] asciiTable;
    private HashMap<String,Memory> buckets = new HashMap<>();
    
    public RAM(){
        asciiTable = new char[256];

        for (int i = 0; i < asciiTable.length; i++)
            asciiTable[i] = (char) i;

        for (int i = 0; i < memory.length; i++)
            memory[i] = false;
    }

    public int allocateMemory(String name, String type, String value){
        if (buckets.get(name) != null)
            return buckets.get(name).allocate(value);
        else{
            Memory data = new Memory(type);
            int OK = data.allocate(value);
            if (OK == 0)
                buckets.put(name,data);
            return OK;
        }
    }

    public int allocateMemoryByMemory(String newVariable, String previous, String type){
        if (buckets.get(previous) == null || !type.equals(buckets.get(previous).type))
            return -2;

        buckets.put(newVariable,buckets.get(previous));
        return 0;
    }

    public Object getValue(String name){
        return buckets.get(name) == null ? null: buckets.get(name).read();
    }

    public boolean deleteValue(String name){
        return buckets.remove(name) != null;
    }

    private class Memory{
        private String type;
        private int size, memoryStartIndex, offsetIndex;

        public Memory(String type){
            this.memoryStartIndex = lastMemory;
            this.type = type;
        }

        private int allocate(String value){
            switch (type){
                case "integer":
                    size = 32;
                    return allocateInteger(value);
                case "double":
                    size = 64;
                    return allocateDouble(value);
                case "char":
                    size = 16;
                    return allocateChar(value);
                case "word":
                    return allocateString(value);
                case "boolean":
                    size = 1;
                    return allocateBoolean(value);
                default:
                    size = 0;
                    return  -2;
            }
        }

        private Object read(){
            switch (type){
                case "integer":
                    return readInt();
                case "double":
                    return readDouble();
                case "char":
                    return readChar();
                case "word":
                    return readString();
                case "boolean":
                    return readBoolean();
                default:
                    return null;
            }
        }

        private int allocateInteger(String value) {
            if (!checkSpace())
                return -1;

            boolean negative = false;

            if (value.charAt(0) == '-'){
                value = value.substring(1);
                negative = true;
            }

            if (!isValidNumber(value, true))
                return -2;

            StringBuilder binary = new StringBuilder(Integer.toBinaryString(Integer.parseInt(value)));

            if (binary.length() > 31)
                return -2;

            while (binary.length() < 31)
                binary.insert(0,'0');

            binary.insert(0,negative ? 1:0);

            for (int i = memoryStartIndex; i < memoryStartIndex + 32; i++)
                memory[i] = binary.charAt(i - memoryStartIndex) != '0';

            if (lastMemory == memoryStartIndex)
                lastMemory += size;

            return 0;
        }

        private int allocateDouble(String value) {
            if (!checkSpace())
                return -1;
            else if (value.length() == 0)
                return -2;

            boolean negative = false;

            if (value.charAt(0) == '-') {
                value = value.substring(1);
                negative = true;
            }

            String[] values = value.split("\\.");

            if (values.length == 1)
                values = new String[]{values[0],"0"};

            if (values.length > 2)
                return -2;

            for (String data: values){
                if (!isValidNumber(data, false))
                    return -2;
            }

            StringBuilder binary1 = new StringBuilder(Long.toBinaryString(Long.parseLong(values[0])));
            StringBuilder binary2 = new StringBuilder(Long.toBinaryString(Long.parseLong(values[1])));

            if (binary1.length() + binary2.length() > 63)
                return -2;

            int binary1Length = 31;

            if (binary2.length() > 32)
                binary1Length = 64 - binary2.length() - 1;

            while (binary1.length() < binary1Length)
                binary1.insert(0,'0');

            binary1.insert(0, negative ? 1 : 0);
            binary1Length = binary1.length();
            offsetIndex = binary1Length;

            while (binary2.length() < 64 - binary1.length())
                binary2.insert(0,'0');

            for (int i = memoryStartIndex; i < memoryStartIndex + binary1Length; i++)
                memory[i] = binary1.charAt(i - memoryStartIndex) != '0';

            for (int i = memoryStartIndex + binary1Length; i < memoryStartIndex + 64; i++)
                memory[i] = binary2.charAt(i - memoryStartIndex - binary1Length) != '0';

            if (lastMemory == memoryStartIndex)
                lastMemory += size;

            return 0;
        }

        private int allocateChar(String value) {
            if (!checkSpace())
                return -1;
            else if (value.length() > 1 || getCharIndex(value.charAt(0)) == -1)
                return -2;
            else if (value.length() == 0)
                value = " ";

            StringBuilder charValue = new StringBuilder(Integer.toBinaryString(getCharIndex(value.charAt(0))));

            while (charValue.length() < 16)
                charValue.insert(0,'0');

            for (int i = memoryStartIndex; i < memoryStartIndex + 16; i++)
                memory[i] = charValue.charAt(i - memoryStartIndex) != '0';

            if (lastMemory == memoryStartIndex)
                lastMemory += size;

            return 0;
        }

        private int allocateBoolean(String value) {
            if (!checkSpace())
                return -1;
            else if (!value.equals("true") && !value.equals("false"))
                return -2;

            memory[memoryStartIndex] = value.equals("true");

            if (lastMemory == memoryStartIndex)
                lastMemory++;

            return 0;
        }

        private int allocateString(String value) {
            if (!checkSpace())
                return -1;
            if (value.length() == 0)
                value += (char) 0;

            if (value.length() * 16 > size){
                memoryStartIndex = lastMemory;
                size = value.length() * 16;
                if (memoryStartIndex + size >= memory.length)
                    reallocate();
            }

            if (value.length() * 16 < size){
                if (lastMemory == memoryStartIndex + size)
                    lastMemory -= (size - value.length() * 16);

                size = value.length() * 16;
            }

            StringBuilder st = new StringBuilder();

            for (int i = 0; i < value.length(); i++) {
                st.append(Integer.toBinaryString(getCharIndex(value.charAt(i))));
                while (st.length() % 16 != 0)
                    st.insert(i * 16, '0');
            }

            for (int i = memoryStartIndex; i < memoryStartIndex + size; i++)
                memory[i] = st.charAt(i - memoryStartIndex) == '1';

            if (lastMemory == memoryStartIndex)
                lastMemory += size;

            return 0;
        }

        private int readInt(){
            StringBuilder data = new StringBuilder();

            for (int i = memoryStartIndex + 1; i < memoryStartIndex + size; i++)
                data.append(memory[i] ? 1: 0);

            if (memory[memoryStartIndex])
                data.insert(0,"-");

            return Integer.parseInt(data.toString(), 2);
        }

        private double readDouble(){
            StringBuilder data1 = new StringBuilder();
            StringBuilder data2 = new StringBuilder();

            for (int i = memoryStartIndex + 1; i < memoryStartIndex + offsetIndex; i++)
                data1.append(memory[i] ? 1: 0);

            for (int i = memoryStartIndex + offsetIndex; i < memoryStartIndex + size; i++)
                data2.append(memory[i] ? 1: 0);

            long part1 = Long.parseLong(data1.toString(),2);
            long part2 = Long.parseLong(data2.toString(),2);

            StringBuilder combined = new StringBuilder(part1 + "." + part2);

            if (memory[memoryStartIndex])
                combined.insert(0,"-");

            return Double.parseDouble(combined.toString());
        }

        private char readChar(){
            StringBuilder data1 = new StringBuilder();

            for (int i = memoryStartIndex; i < memoryStartIndex + size; i++)
                data1.append(memory[i] ? 1:0);


            return (char) Integer.parseInt(data1.toString(),2);
        }

        private boolean readBoolean(){
            return memory[memoryStartIndex];
        }

        private String readString(){
            StringBuilder string = new StringBuilder();

            for (int i = 0; i < size / 16; i++) {
                StringBuilder data1 = new StringBuilder();

                for (int j = memoryStartIndex + i*16; j < memoryStartIndex + (i+1) * 16; j++)
                    data1.append(memory[j] ? 1 : 0);

                string.append((char)Integer.parseInt(data1.toString(),2));
            }

            return string.toString();
        }

        private boolean checkSpace(){
            if (memoryStartIndex != lastMemory - size && lastMemory + size > memory.length) {
                reallocate();
                memoryStartIndex = lastMemory;

                if (memoryStartIndex + size > memory.length)
                    return false;
            }
            return true;
        }

        public String toString(){
            return type + ":" + size + " [" + memoryStartIndex + (offsetIndex == 0 ? "": "-" + offsetIndex) + "]";
        }
    }

    private int getCharIndex(char c){
        for (int i = 0; i < asciiTable.length; i++)
            if (asciiTable[i] == c)
                return i;

        return -1;
    }
    
    private void reallocate(){
        boolean[] memoryCopy = new boolean[memory.length];
        int tempLastIndex = 0;

        for (String name: buckets.keySet()){
            Memory data = buckets.get(name);
            System.arraycopy(memory, data.memoryStartIndex + tempLastIndex - tempLastIndex, memoryCopy, tempLastIndex, tempLastIndex + data.size - tempLastIndex);
            data.memoryStartIndex = tempLastIndex;
            tempLastIndex += data.size;
        }

        memory = memoryCopy;
        lastMemory = tempLastIndex;
    }

    private boolean isValidNumber(String value, boolean isInt){
        boolean stillMatching = false;

        if (isInt && value.length() > 10 || !isInt && value.length() > 19 || value.length() == 0)
            return false;
        else if (isInt && value.length() == 10 || !isInt && value.length() == 19)
            stillMatching = true;

        String maxInt = "2147483647";
        String maxDouble = "9223372036854775807";

        if (isInt && value.length() > maxInt.length() || !isInt && value.length() > maxDouble.length())
            return false;

        for (int i = 0; i < value.length(); i++) {
            if ((isInt && value.length() == 10 || !isInt && value.length() == 19) && stillMatching){
                if ("0123456789".indexOf(value.charAt(i)) == -1)
                    return false;
                else {
                    int thisValue = Integer.parseInt(value.charAt(i) + "");
                    int maxValue = Integer.parseInt(((isInt) ? maxInt : maxDouble).charAt(i) + "");

                    if (thisValue < maxValue)
                        stillMatching = false;
                    else if (thisValue > maxValue)
                        return false;
                }
            }
            else if ("0123456789".indexOf(value.charAt(i)) == -1)
                return false;
        }

        return true;
    }
}