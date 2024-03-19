import type { Socket } from 'node:net';

import logger from './log';

export class DataBuffer {
  private buffer: Buffer;

  private length: number;

  private handler: (message: string) => any;

  constructor(handler: (message: string) => any, size: number = 2 ** 13) {
    this.buffer = Buffer.alloc(size);
    this.length = 0;
    this.handler = handler;
  }

  public add = (data: Buffer): void => {
    const headerLength = 2;
    const dataLength = data.length;
    data.copy(this.buffer, this.length, 0, dataLength);
    this.length += dataLength;
    while (this.length >= headerLength) {
      try {
        const textLength: number = this.buffer.readUInt16BE(); // needs to be 2 bytes
        logger.debug(`Text length: ${textLength}`);
        // this should catch 0 and -1 (end of stream)
        if (textLength <= 0 || textLength > this.buffer.length) throw new Error('Invalid message size');
        if (this.length < headerLength + textLength) throw new Error('Not enough data');
        const receivedMessage: string = this.buffer.subarray(headerLength, headerLength + textLength).toString('utf8');
        logger.debug(`Message: ${receivedMessage}`);
        this.handler(receivedMessage);
        this.buffer.copy(this.buffer, 0, 2 + textLength, this.length);
        this.length -= headerLength + textLength;
      } catch (e: any) {
        logger.error(`Error parsing message: ${e.message}`);
        break;
      }
    }
  };
}

export const send = (socket: Socket, payload: string): void => {
  try {
    logger.debug(`Sending: ${payload}`);
    const sentBytes: Buffer = Buffer.from(payload, 'utf8');
    const lengthBuffer: Buffer = Buffer.alloc(2);
    lengthBuffer.writeUInt16BE(payload.length);
    socket.write(lengthBuffer, (err) => {
      if (err) throw new Error('Error sending message length');
    });
    socket.write(sentBytes, (err) => {
      if (err) throw new Error('Error sending message');
    });
  } catch (e) {
    logger.error(e);
  }
};
