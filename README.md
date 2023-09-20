### ANN-Secp256k1

Experimenting with artificial neural networks and ecc math (curve secp256k1).

Using [this simple scala neural net library](https://github.com/mrdimosthenis/scala-synapses) for the
neural network side of things, and [this simple pure scala secp256k1 library](https://github.com/VzxPLnHqr/secp256k1-scala). 
Using [cats-effect](https://typelevel.org/cats-effect/) for everything else.

#### Usage
1. `scala-cli run .`
2. ... wait forever ...
3. ... keep waiting ...
4. network **will learn to recover a private key from a public key** and generator point
5. ... but it is not likely to happen anytime soon!
7. in case it does though, the network is saved to disk after every 100 random training observations

#### Status - just for fun
