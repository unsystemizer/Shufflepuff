package com.shuffle.sim;

import com.shuffle.bitcoin.Crypto;
import com.shuffle.bitcoin.SigningKey;
import com.shuffle.bitcoin.VerificationKey;
import com.shuffle.protocol.InvalidImplementationError;
import com.shuffle.protocol.Machine;
import com.shuffle.protocol.MessageFactory;
import com.shuffle.protocol.Network;
import com.shuffle.protocol.SessionIdentifier;
import com.shuffle.protocol.SignedPacket;
import com.shuffle.protocol.TimeoutError;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * A simulator for running integration tests on the protocol.
 *
 * Created by Daniel Krawisz on 12/6/15.
 */
public final class Simulator {
    private static Logger log= LogManager.getLogger(Simulator.class);

    /**
     * A network connecting different players together in a simulation.
     *
     * Created by Daniel Krawisz on 2/8/16.
     */
    private class NetworkSim implements Network {
        final BlockingQueue<SignedPacket> inbox = new LinkedBlockingQueue<>();
        final Map<VerificationKey, NetworkSim> networks;

        NetworkSim(Map<VerificationKey, NetworkSim> networks) {
            if (networks == null) {
                throw new NullPointerException();
            }
            this.networks = networks;
        }

        @Override
        public void sendTo(VerificationKey to, SignedPacket packet) throws InvalidImplementationError, TimeoutError {

            try {
                networks.get(to).deliver(packet);
            } catch (InterruptedException e) {
                // This means that the thread running the machine we are delivering to has been interrupted.
                // This would look like a timeout if this were happening over a real network.
                throw new TimeoutError();
            }
        }

        @Override
        public SignedPacket receive() throws TimeoutError, InterruptedException {
            for (int i = 0; i < 2; i++) {
                SignedPacket next = inbox.poll(1, TimeUnit.SECONDS);

                if (next != null) {
                    return next;
                }
            }

            throw new TimeoutError();
        }

        public void deliver(SignedPacket packet) throws InterruptedException {
            inbox.put(packet);
        }
    }

    final MessageFactory messages;

    public Simulator(MessageFactory messages)  {
        this.messages = messages;
    }

    public Map<SigningKey, Machine> run(InitialState init, Crypto crypto) {

        final Map<SigningKey, Adversary> machines = new HashMap<>();
        final Map<VerificationKey, NetworkSim> networks = new HashMap<>();

        // Check that all players have a coin network set up, either the default or their own.
        for (InitialState.PlayerInitialState player : init.getPlayers()) {
            if (player.sk == null) {
                continue;
            }

            NetworkSim network = new NetworkSim(networks);
            networks.put(player.vk, network);

            Adversary adversary = player.adversary(crypto, messages, network);
            machines.put(player.sk, adversary);
        }

        Map<SigningKey, Machine> results = runSimulation(machines);

        networks.clear(); // Avoid memory leak.
        return results;
    }

    private static synchronized Map<SigningKey, Machine> runSimulation(
            Map<SigningKey, Adversary> machines)  {

        Map<SigningKey, Future<Machine>> wait = new HashMap<>();
        Map<SigningKey, Machine> results = new HashMap<>();

        // Start the simulation.
        for (Map.Entry<SigningKey, Adversary> in : machines.entrySet()) {
            wait.put(in.getKey(), in.getValue().turnOn());
        }

        while (wait.size() != 0) {
            Iterator<Map.Entry<SigningKey, Future<Machine>>> i = wait.entrySet().iterator();
            while (i.hasNext()) {
                Map.Entry<SigningKey, Future<Machine>> entry = i.next();
                Future<Machine> future = entry.getValue();
                if (future.isDone()) {
                    try {
                        Machine machine = future.get();
                        results.put(entry.getKey(), machine);
                    } catch (InterruptedException | ExecutionException e) {
                        e.printStackTrace();
                    }

                    i.remove();
                }
            }
        }

        return results;
    }
}
