package io.github.cpsc559.team16.common.dto;

/**
 * Defines the role of an Addressing Server in the distributed network.
 * <p>
 * Addressing Servers operate in one of two roles:
 * </p>
 * <ul>
 *     <li><strong>PRIMARY</strong> - The leader process managing connections and updates.</li>
 *     <li><strong>REPLICA</strong> - A passive replica that receives updates from the primary.</li>
 * </ul>
 */
public enum ServerRole {
    PRIMARY, REPLICA
}
