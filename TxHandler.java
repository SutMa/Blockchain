public class TxHandler {
    private UTXOPool utxoPool;
  
    public TxHandler(UTXOPool utxoPool) {
        this.utxoPool = new UTXOPool(utxoPool);
    }
  
    public boolean isValidTx(Transaction tx) {
        double inputSum = 0.0;
        double outputSum = 0.0;
        UTXOPool uniqueUtxos = new UTXOPool();
  
        for (int i = 0; i < tx.numInputs(); i++) {
            Transaction.Input input = tx.getInput(i);
            UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
  
            if (!utxoPool.contains(utxo) || uniqueUtxos.contains(utxo)) {
                return false;
            }
  
            Transaction.Output output = utxoPool.getTxOutput(utxo);
            if (!Crypto.verifySignature(output.address, tx.getRawDataToSign(i), input.signature)) {
                return false;
            }
  
            inputSum += output.value;
            uniqueUtxos.addUTXO(utxo, output);
        }
  
        for (Transaction.Output output : tx.getOutputs()) {
            if (output.value < 0) {
                return false;
            }
            outputSum += output.value;
        }
  
        return inputSum >= outputSum;
    }
  
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        ArrayList<Transaction> validTransactions = new ArrayList<>();
  
        boolean newTxAdded;
  
        do {
            newTxAdded = false;
  
            for (Transaction tx : possibleTxs) {
                if (validTransactions.contains(tx) || !isValidTx(tx)) continue;
  
                validTransactions.add(tx);
  
                for (Transaction.Input input : tx.getInputs()) {
                    UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
                    utxoPool.removeUTXO(utxo);
                }
  
                for (int i = 0; i < tx.numOutputs(); i++) {
                    Transaction.Output output = tx.getOutput(i);
                    UTXO utxo = new UTXO(tx.getHash(), i);
                    utxoPool.addUTXO(utxo, output);
                }
  
                newTxAdded = true;
            }
        } while (newTxAdded);
  
        Transaction[] validTxsArray = new Transaction[validTransactions.size()];
        return validTransactions.toArray(validTxsArray);
    }
  }
  