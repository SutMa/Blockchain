

public class BlockChain {
    public static final int CUT_OFF_AGE = 10;
    private HashMap<ByteArrayWrapper, BlockNode> blockChain;
    private TransactionPool txPool;
    private BlockNode maxHeightBlockNode;

    private class BlockNode {
        public Block block;
        public BlockNode parent;
        public int height;
        public UTXOPool utxoPool;

        public BlockNode(Block block, BlockNode parent, UTXOPool uPool) {
            this.block = block;
            this.parent = parent;
            if (parent != null) {
                height = parent.height + 1;
            } else {
                height = 1;
            }
            this.utxoPool = new UTXOPool(uPool);
        }
    }

    public BlockChain(Block genesisBlock) {
        blockChain = new HashMap<>();
        UTXOPool uPool = new UTXOPool();
        addCoinbaseToUTXOPool(genesisBlock, uPool);
        BlockNode genesisNode = new BlockNode(genesisBlock, null, uPool);
        blockChain.put(new ByteArrayWrapper(genesisBlock.getHash()), genesisNode);
        txPool = new TransactionPool();
        maxHeightBlockNode = genesisNode;
    }

    public Block getMaxHeightBlock() {
        return maxHeightBlockNode.block;
    }

    public UTXOPool getMaxHeightUTXOPool() {
        return maxHeightBlockNode.utxoPool;
    }

    public TransactionPool getTransactionPool() {
        return txPool;
    }

    public boolean addBlock(Block block) {
        byte[] prevBlockHash = block.getPrevBlockHash();
        if (prevBlockHash == null) return false;

        BlockNode parentBlockNode = blockChain.get(new ByteArrayWrapper(prevBlockHash));
        if (parentBlockNode == null) return false;

        TxHandler handler = new TxHandler(parentBlockNode.utxoPool);
        Transaction[] txs = block.getTransactions().toArray(new Transaction[0]);
        Transaction[] validTxs = handler.handleTxs(txs);

        if (validTxs.length != txs.length) return false;

        int proposedHeight = parentBlockNode.height + 1;
        if (proposedHeight <= maxHeightBlockNode.height - CUT_OFF_AGE) return false;

        UTXOPool uPool = handler.utxoPool; // Retrieve the updated UTXOPool after handling transactions
        addCoinbaseToUTXOPool(block, uPool);
        BlockNode newNode = new BlockNode(block, parentBlockNode, uPool);
        blockChain.put(new ByteArrayWrapper(block.getHash()), newNode);

        if (proposedHeight > maxHeightBlockNode.height) {
            maxHeightBlockNode = newNode;
        }

        // Limiting the size of block nodes
        if (blockChain.size() > CUT_OFF_AGE + 1) {
            ByteArrayWrapper minHeightBlockHash = findMinHeightBlockHash();
            if (minHeightBlockHash != null) {
                blockChain.remove(minHeightBlockHash);
            }
        }

        return true;
    }

    public void addTransaction(Transaction tx) {
        txPool.addTransaction(tx);
    }

    private void addCoinbaseToUTXOPool(Block block, UTXOPool uPool) {
        Transaction coinbase = block.getCoinbase();
        for (int i = 0; i < coinbase.numOutputs(); i++) {
            Transaction.Output out = coinbase.getOutput(i);
            UTXO utxo = new UTXO(coinbase.getHash(), i);
            uPool.addUTXO(utxo, out);
        }
    }

    private ByteArrayWrapper findMinHeightBlockHash() {
        int minHeight = Integer.MAX_VALUE;
        ByteArrayWrapper minHeightBlockHash = null;
        for (Map.Entry<ByteArrayWrapper, BlockNode> entry : blockChain.entrySet()) {
            if (entry.getValue().height < minHeight) {
                minHeight = entry.getValue().height;
                minHeightBlockHash = entry.getKey();
            }
        }
        return minHeightBlockHash;
    }
}
